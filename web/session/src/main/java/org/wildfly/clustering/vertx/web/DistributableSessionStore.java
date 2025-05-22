/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.SessionStore;

import org.jboss.logging.Logger;
import org.wildfly.clustering.cache.batch.Batch;
import org.wildfly.clustering.cache.batch.BatchContext;
import org.wildfly.clustering.cache.batch.SuspendedBatch;
import org.wildfly.clustering.server.util.MapEntry;
import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.Session;
import org.wildfly.clustering.session.SessionManager;
import org.wildfly.clustering.session.SessionManagerConfiguration;
import org.wildfly.clustering.session.SessionManagerFactory;
import org.wildfly.common.function.Functions;

/**
 * A distributable Vert.x session store.
 */
public class DistributableSessionStore implements SessionStore {
	private static final Logger LOGGER = Logger.getLogger(DistributableSessionStore.class);

	private final BiFunction<Context, JsonObject, SessionManagerFactory<Context, Void>> factory;
	private final Runnable closeTask;
	private final StampedLock lifecycleLock = new StampedLock();

	private volatile Context context;
	private volatile SessionManager<Void> manager;
	private volatile OptionalLong lifecycleStamp = OptionalLong.empty();

	public DistributableSessionStore(BiFunction<Context, JsonObject, SessionManagerFactory<Context, Void>> factory, Runnable closeTask) {
		this.factory = factory;
		this.closeTask = closeTask;
	}

	@Override
	public SessionStore init(Vertx vertx, JsonObject options) {
		Context context = vertx.getOrCreateContext();
		this.context = context;
		SessionManagerFactory<Context, Void> factory = this.factory.apply(this.context, options);
		Supplier<String> identifierFactory = new VertxSessionIdentifierFactory(this.context);
		this.manager = factory.createSessionManager(new SessionManagerConfiguration<>() {
			@Override
			public Supplier<String> getIdentifierFactory() {
				return identifierFactory;
			}

			@Override
			public Consumer<ImmutableSession> getExpirationListener() {
				return Functions.discardingConsumer();
			}

			@Override
			public Duration getTimeout() {
				return Duration.ofMillis(SessionHandler.DEFAULT_SESSION_TIMEOUT);
			}

			@Override
			public Context getContext() {
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
		Map.Entry<Batch, Runnable> batchEntry = this.createBatchEntry();
		try {
			Session<Void> session = this.manager.createSession(id);
			session.getMetaData().setTimeout(Duration.ofMillis(timeout));
			return new DistributableSession(this.manager, session, batchEntry.getKey().suspend(), batchEntry.getValue());
		} catch (RuntimeException | Error e) {
			rollback(batchEntry.getKey(), batchEntry.getValue());
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
		return this.context.executeBlocking(this::createSuspendedBatchEntry)
				.compose(entry -> {
					try (BatchContext<Batch> batch = entry.getKey().resumeWithContext()) {
						return Future.fromCompletionStage(this.manager.findSessionAsync(id), this.context)
								.map(session -> (session != null) ? new DistributableSession(this.manager, session, entry.getKey(), entry.getValue()) : rollback(entry))
								.onFailure(e -> rollback(entry));
					}
				});
	}

	private MapEntry<SuspendedBatch, Runnable> createSuspendedBatchEntry() {
		return this.createBatchEntry().map(Batch::suspend, Function.identity());
	}

	private MapEntry<Batch, Runnable> createBatchEntry() {
		Runnable closeTask = this.getSessionCloseTask();
		try {
			return MapEntry.of(this.manager.getBatchFactory().get(), closeTask);
		} catch (RuntimeException | Error e) {
			closeTask.run();
			throw e;
		}
	}

	private static io.vertx.ext.web.Session rollback(Map.Entry<SuspendedBatch, Runnable> entry) {
		rollback(entry.getKey().resume(), entry.getValue());
		return null;
	}

	private static void rollback(Batch resumedBatch, Runnable finallyTask) {
		try (Batch batch = resumedBatch) {
			batch.discard();
		} catch (RuntimeException | Error e) {
			LOGGER.warn(e.getLocalizedMessage(), e);
		} finally {
			finallyTask.run();
		}
	}

	@Override
	public Future<Void> delete(String id) {
		// Do nothing - DistributableSession.regenerateId already removed the old session
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> put(io.vertx.ext.web.Session session) {
		if (session instanceof VertxSession) {
			VertxSession vertxSession = (VertxSession) session;
			return this.context.executeBlocking(() -> {
				vertxSession.close();
				return null;
			});
		}
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
		if (this.lifecycleStamp.isEmpty()) {
			try {
				this.lifecycleStamp = OptionalLong.of(this.lifecycleLock.writeLockInterruptibly());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
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
					lock.unlock(stamp);
				}
			}
		};
	}
}
