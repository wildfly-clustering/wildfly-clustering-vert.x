/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiFunction;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.SessionStore;

import org.jboss.logging.Logger;
import org.wildfly.clustering.cache.batch.Batch;
import org.wildfly.clustering.cache.batch.SuspendedBatch;
import org.wildfly.clustering.context.Context;
import org.wildfly.clustering.function.Consumer;
import org.wildfly.clustering.function.Supplier;
import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.Session;
import org.wildfly.clustering.session.SessionManager;
import org.wildfly.clustering.session.SessionManagerConfiguration;
import org.wildfly.clustering.session.SessionManagerFactory;

/**
 * A distributable Vert.x session store.
 */
public class DistributableSessionStore implements SessionStore {
	private static final Logger LOGGER = Logger.getLogger(DistributableSessionStore.class);

	private final BiFunction<io.vertx.core.Context, JsonObject, SessionManagerFactory<io.vertx.core.Context, Void>> factory;
	private final Runnable closeTask;
	private final StampedLock lifecycleLock = new StampedLock();

	private volatile io.vertx.core.Context context;
	private volatile SessionManager<Void> manager;

	public DistributableSessionStore(BiFunction<io.vertx.core.Context, JsonObject, SessionManagerFactory<io.vertx.core.Context, Void>> factory, Runnable closeTask) {
		this.factory = factory;
		this.closeTask = closeTask;
	}

	@Override
	public SessionStore init(Vertx vertx, JsonObject options) {
		io.vertx.core.Context context = vertx.getOrCreateContext();
		this.context = context;
		SessionManagerFactory<io.vertx.core.Context, Void> factory = this.factory.apply(this.context, options);
		Supplier<String> identifierFactory = new VertxSessionIdentifierFactory(this.context);
		this.manager = factory.createSessionManager(new SessionManagerConfiguration<>() {
			@Override
			public Supplier<String> getIdentifierFactory() {
				return identifierFactory;
			}

			@Override
			public Consumer<ImmutableSession> getExpirationListener() {
				return Consumer.empty();
			}

			@Override
			public Duration getTimeout() {
				return Duration.ofMillis(SessionHandler.DEFAULT_SESSION_TIMEOUT);
			}

			@Override
			public io.vertx.core.Context getContext() {
				return context;
			}
		});
		this.manager.start();
		return this;
	}

	@Override
	public long retryTimeout() {
		return 0;
	}

	@Override
	public io.vertx.ext.web.Session createSession(long timeout) {
		String id = this.manager.getIdentifierFactory().get();
		Map.Entry<SuspendedBatch, Runnable> entry = this.createBatchEntry();
		SuspendedBatch suspendedBatch = entry.getKey();
		Runnable closeTask = entry.getValue();
		try (Context<Batch> context = suspendedBatch.resumeWithContext()) {
			Session<Void> session = this.manager.createSession(id);
			session.getMetaData().setTimeout(Duration.ofMillis(timeout));
			return new DistributableSession(this.manager, session, suspendedBatch, closeTask);
		} catch (RuntimeException | Error e) {
			rollback(entry);
			throw e;
		}
	}

	@Override
	public io.vertx.ext.web.Session createSession(long timeout, int length) {
		// Length is pre-configured
		return this.createSession(timeout);
	}

	@Override
	public Future<io.vertx.ext.web.Session> get(String id) {
		return this.context.executeBlocking(this::createBatchEntry)
				.compose(entry -> {
					try (Context<Batch> context = entry.getKey().resumeWithContext()) {
						return Future.fromCompletionStage(this.manager.findSessionAsync(id), this.context)
								.map(session -> ((session != null) && session.isValid() && !session.getMetaData().isExpired()) ? new DistributableSession(this.manager, session, entry.getKey(), entry.getValue()) : close(entry))
								.onFailure(e -> rollback(entry));
					}
				});
	}

	private Map.Entry<SuspendedBatch, Runnable> createBatchEntry() {
		Runnable closeTask = this.getSessionCloseTask();
		try {
			return Map.entry(this.manager.getBatchFactory().get().suspend(), closeTask);
		} catch (RuntimeException | Error e) {
			closeTask.run();
			throw e;
		}
	}

	private static io.vertx.ext.web.Session close(Map.Entry<SuspendedBatch, Runnable> entry) {
		close(entry, Consumer.empty());
		return null;
	}

	private static void rollback(Map.Entry<SuspendedBatch, Runnable> entry) {
		close(entry, Batch::discard);
	}

	private static void close(Map.Entry<SuspendedBatch, Runnable> entry, Consumer<Batch> batchTask) {
		try (Context<Batch> context = entry.getKey().resumeWithContext()) {
			try (Batch batch = context.get()) {
				batchTask.accept(batch);
			}
		} catch (RuntimeException | Error e) {
			LOGGER.warn(e.getLocalizedMessage(), e);
		} finally {
			entry.getValue().run();
		}
	}

	@Override
	public Future<Void> delete(String id) {
		// Do nothing - DistributableSession.regenerateId already removed the old session
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> put(io.vertx.ext.web.Session session) {
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> clear() {
		return Future.succeededFuture();
	}

	@Override
	public Future<Integer> size() {
		return Future.succeededFuture(Long.valueOf(this.manager.getStatistics().getActiveSessionCount()).intValue());
	}

	@Override
	public void close() {
		try {
			this.lifecycleLock.writeLockInterruptibly();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		try {
			this.manager.stop();
		} finally {
			this.closeTask.run();
		}
	}

	private Runnable getSessionCloseTask() {
		StampedLock lock = this.lifecycleLock;
		long stamp = lock.tryReadLock();
		if (!StampedLock.isReadLockStamp(stamp)) {
			throw new IllegalStateException();
		}
		AtomicLong stampRef = new AtomicLong(stamp);
		return new Runnable() {
			@Override
			public void run() {
				// Ensure we only unlock once.
				long stamp = stampRef.getAndSet(0L);
				if (StampedLock.isReadLockStamp(stamp)) {
					lock.unlockRead(stamp);
				}
			}
		};
	}
}
