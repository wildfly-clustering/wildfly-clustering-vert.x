/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.wildfly.clustering.cache.batch.Batch;
import org.wildfly.clustering.cache.batch.SuspendedBatch;
import org.wildfly.clustering.context.Context;
import org.wildfly.clustering.session.Session;
import org.wildfly.clustering.session.SessionManager;

/**
 * A distributable Vert.x session.
 */
public class DistributableSession implements VertxSession {
	private static final System.Logger LOGGER = System.getLogger(DistributableSession.class.getPackageName());

	private final SessionManager<Void> manager;
	private final SuspendedBatch batch;
	private final Runnable closeTask;
	private final Instant startTime;

	private volatile String originalId;
	private volatile Session<Void> session;

	public DistributableSession(SessionManager<Void> manager, Session<Void> session, SuspendedBatch batch, Runnable closeTask) {
		this.manager = manager;
		this.session = session;
		this.batch = batch;
		this.closeTask = closeTask;
		this.startTime = session.getMetaData().isNew() ? session.getMetaData().getCreationTime() : Instant.now();
		this.originalId = session.getId();
	}

	@Override
	public io.vertx.ext.web.Session regenerateId() {
		Session<Void> oldSession = this.session;
		String id = this.manager.getIdentifierFactory().get();
		try (Context<Batch> context = this.batch.resumeWithContext()) {
			Session<Void> newSession = this.manager.createSession(id);
			try {
				for (Map.Entry<String, Object> entry : oldSession.getAttributes().entrySet()) {
					newSession.getAttributes().put(entry.getKey(), entry.getValue());
				}
				newSession.getMetaData().setTimeout(oldSession.getMetaData().getTimeout());
				newSession.getMetaData().setLastAccess(oldSession.getMetaData().getLastAccessStartTime(), oldSession.getMetaData().getLastAccessTime());
				oldSession.invalidate();
				this.session = newSession;
				oldSession.close();
			} catch (IllegalStateException e) {
				newSession.invalidate();
				throw e;
			}
		}
		return this;
	}

	@Override
	public String id() {
		return this.session.getId();
	}

	@Override
	public io.vertx.ext.web.Session put(String key, Object value) {
		this.session.getAttributes().put(key, value);
		return this;
	}

	@Override
	public io.vertx.ext.web.Session putIfAbsent(String key, Object value) {
		this.session.getAttributes().putIfAbsent(key, value);
		return this;
	}

	@Override
	public io.vertx.ext.web.Session computeIfAbsent(String key, Function<String, Object> mappingFunction) {
		this.session.getAttributes().computeIfAbsent(key, mappingFunction);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String key) {
		return (T) this.session.getAttributes().get(key);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T remove(String key) {
		return (T) this.session.getAttributes().remove(key);
	}

	@Override
	public Map<String, Object> data() {
		return this.session.getAttributes();
	}

	@Override
	public boolean isEmpty() {
		return this.session.getAttributes().isEmpty();
	}

	@Override
	public long lastAccessed() {
		return this.session.getMetaData().getLastAccessTime().toEpochMilli();
	}

	@Override
	public void destroy() {
		this.close(Session::invalidate);
	}

	@Override
	public boolean isDestroyed() {
		return !this.session.isValid();
	}

	@Override
	public boolean isRegenerated() {
		return this.session.getId() != this.originalId;
	}

	@Override
	public String oldId() {
		return this.originalId;
	}

	@Override
	public long timeout() {
		return this.session.getMetaData().getTimeout().toMillis();
	}

	@Override
	public void close() {
		this.close(session -> session.getMetaData().setLastAccess(this.startTime, Instant.now()));
	}

	private void close(Consumer<Session<Void>> closeTask) {
		try (Context<Batch> context = this.batch.resumeWithContext()) {
			try (Batch batch = context.get()) {
				try (Session<Void> session = this.session) {
					if (session.isValid()) {
						closeTask.accept(session);
					}
				}
			}
		} catch (RuntimeException | Error e) {
			LOGGER.log(System.Logger.Level.WARNING, e.getLocalizedMessage(), e);
		} finally {
			this.closeTask.run();
		}
	}
}
