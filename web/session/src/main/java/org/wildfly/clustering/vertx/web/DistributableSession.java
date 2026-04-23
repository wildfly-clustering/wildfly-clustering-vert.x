/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.wildfly.clustering.function.Consumer;
import org.wildfly.clustering.function.Function;
import org.wildfly.clustering.function.Predicate;
import org.wildfly.clustering.server.util.BlockingReference;
import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.ImmutableSessionMetaData;
import org.wildfly.clustering.session.Session;
import org.wildfly.clustering.session.SessionManager;
import org.wildfly.clustering.session.SessionMetaData;

/**
 * A distributable Vert.x session.
 */
public class DistributableSession implements VertxSession {

	private final SessionManager<Void> manager;
	private final BlockingReference<Session<Void>> reference;
	private final AtomicReference<Runnable> closeTask;
	private final Instant startTime;
	private final String originalId;

	/**
	 * Creates a distributable Vert.x session backed by the specified session.
	 * @param manager the manager of the specified session
	 * @param session the decorated session
	 * @param closeTask a task to invoke on {@link VertxSession#close()}.
	 */
	public DistributableSession(SessionManager<Void> manager, Session<Void> session, Runnable closeTask) {
		this.manager = manager;
		this.reference = BlockingReference.of(session);
		this.closeTask = new AtomicReference<>(closeTask);
		this.startTime = session.getMetaData().getLastAccess().isPresent() ? session.getMetaData().getCreationTime() : Instant.now();
		this.originalId = session.getId();
	}

	@Override
	public io.vertx.ext.web.Session regenerateId() {
		String id = this.manager.getIdentifierFactory().get();
		this.reference.getWriter(ImmutableSession.VALID).update(currentSession -> {
			SessionMetaData currentMetaData = currentSession.getMetaData();
			Map<String, Object> currentAttributes = currentSession.getAttributes();
			Session<Void> newSession = this.manager.createSession(id);
			try {
				newSession.getAttributes().putAll(currentAttributes);
				SessionMetaData newMetaData = newSession.getMetaData();
				currentMetaData.getMaxIdle().ifPresent(newMetaData::setMaxIdle);
				currentMetaData.getLastAccess().ifPresent(newMetaData::setLastAccess);
				currentSession.invalidate();
				return newSession;
			} catch (RuntimeException | Error e) {
				newSession.invalidate();
				throw e;
			} finally {
				Consumer.close().accept(newSession.isValid() ? currentSession : newSession);
			}
		});
		return this;
	}

	@Override
	public String id() {
		return this.reference.getReader().map(ImmutableSession.IDENTIFIER).get();
	}

	@Override
	public io.vertx.ext.web.Session put(String key, Object value) {
		this.reference.getReader().map(Session.ATTRIBUTES).map(Session.SET_ATTRIBUTE.composeUnary(Function.identity(), Function.of(Map.entry(key, value)))).get();
		return this;
	}

	@Override
	public io.vertx.ext.web.Session putIfAbsent(String key, Object value) {
		this.reference.getReader().map(Session.ATTRIBUTES).map(attributes -> attributes.putIfAbsent(key, value)).get();
		return this;
	}

	@Override
	public io.vertx.ext.web.Session computeIfAbsent(String key, java.util.function.Function<String, Object> mappingFunction) {
		this.reference.getReader().map(Session.ATTRIBUTES).map(attributes -> attributes.computeIfAbsent(key, mappingFunction)).get();
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String key) {
		return (T) this.reference.getReader().map(Session.ATTRIBUTES).map(ImmutableSession.GET_ATTRIBUTE.composeUnary(Function.identity(), Function.of(key))).get();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T remove(String key) {
		return (T) this.reference.getReader().map(Session.ATTRIBUTES).map(Session.REMOVE_ATTRIBUTE.composeUnary(Function.identity(), Function.of(key))).get();
	}

	@Override
	public Map<String, Object> data() {
		return this.reference.getReader().map(Session.ATTRIBUTES).get();
	}

	@Override
	public boolean isEmpty() {
		return this.reference.getReader().map(ImmutableSession.ATTRIBUTES).map(ImmutableSession.ATTRIBUTE_NAMES).get().isEmpty();
	}

	@Override
	public long lastAccessed() {
		return this.reference.getReader().map(ImmutableSession.METADATA).map(ImmutableSessionMetaData.LAST_ACCESS_TIME).get().toEpochMilli();
	}

	@Override
	public void destroy() {
		Runnable closeTask = this.closeTask.getAndSet(null);
		try {
			this.reference.getReader().read(invalidSession -> {
				try (Session<Void> session = invalidSession) {
					session.invalidate();
				}
			});
		} finally {
			if (closeTask != null) {
				closeTask.run();
			}
		}
	}

	@Override
	public boolean isDestroyed() {
		return this.reference.getReader().map(ImmutableSession.VALID.negate().thenBox()).get();
	}

	@Override
	public boolean isRegenerated() {
		return this.reference.getReader().map(ImmutableSession.IDENTIFIER).map(Predicate.equalTo(this.originalId).thenBox()).get();
	}

	@Override
	public String oldId() {
		return this.originalId;
	}

	@Override
	public long timeout() {
		return this.reference.getReader().map(ImmutableSession.METADATA).map(ImmutableSessionMetaData.MAX_IDLE).get().orElse(Duration.ZERO).toMillis();
	}

	@Override
	public void close() {
		Runnable closeTask = this.closeTask.getAndSet(null);
		if (closeTask != null) {
			try {
				this.reference.getReader().read(completeSession -> {
					try (Session<Void> session = completeSession) {
						session.getMetaData().setLastAccess(this.startTime, Instant.now());
					}
				});
			} finally {
				closeTask.run();
			}
		}
	}
}
