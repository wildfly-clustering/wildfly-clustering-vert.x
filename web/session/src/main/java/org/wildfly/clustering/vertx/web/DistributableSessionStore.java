/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.SessionStore;

import org.wildfly.clustering.function.BiFunction;
import org.wildfly.clustering.function.Consumer;
import org.wildfly.clustering.function.Function;
import org.wildfly.clustering.function.Predicate;
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

	private final BiFunction<io.vertx.core.Context, JsonObject, SessionManagerFactory<io.vertx.core.Context, Void>> factory;
	private final Runnable closeTask;
	private final StampedLock lifecycleLock = new StampedLock();

	private volatile io.vertx.core.Context context;
	private volatile SessionManager<Void> manager;

	/**
	 * Creates a new distributable Vert.x session store.
	 * @param factory a function for creating a session manager factory.
	 * @param closeTask a task to run on {@link SessionStore#close()}.
	 */
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
				return Consumer.of();
			}

			@Override
			public Optional<Duration> getMaxIdle() {
				return Optional.of(Duration.ofMillis(SessionHandler.DEFAULT_SESSION_TIMEOUT)).filter(Predicate.not(Duration::isZero).and(Predicate.not(Duration::isNegative)));
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
		Runnable closeTask = this.getSessionCloseTask();
		try {
			Session<Void> session = this.manager.createSession(id);
			try {
				session.getMetaData().setMaxIdle(Duration.ofMillis(timeout));
				return new DistributableSession(this.manager, session, closeTask);
			} catch (RuntimeException | Error e) {
				Consumer.close().accept(session);
				throw e;
			}
		} catch (RuntimeException | Error e) {
			closeTask.run();
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
		return this.context.executeBlocking(this::getSessionCloseTask)
				.compose(closeTask -> Future.fromCompletionStage(this.manager.findSessionAsync(id), this.context)
				.map(Function.when(Objects::nonNull, Function.<Session<Void>, io.vertx.ext.web.Session>when(ImmutableSession.VALID, session -> new DistributableSession(this.manager, session, closeTask), Function.of(Consumer.of().thenRun(closeTask), Supplier.of(null))), Function.of(null)))
				.onFailure(e -> closeTask.run()));
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
