/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import io.vertx.core.Context;

import org.wildfly.clustering.function.Consumer;
import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.spec.SessionEventListenerSpecificationProvider;
import org.wildfly.clustering.session.spec.SessionSpecificationProvider;

/**
 * Provides a specification provider for Vert.x Web
 * @author Paul Ferraro
 */
public enum VertxSessionSpecificationProvider implements SessionSpecificationProvider<ImmutableSession, Context>, SessionEventListenerSpecificationProvider<ImmutableSession, Void> {
	/** Singleton instance */
	INSTANCE;

	@Override
	public ImmutableSession asSession(ImmutableSession session, Context context) {
		return session;
	}

	@Override
	public Class<Void> getEventListenerClass() {
		return Void.class;
	}

	@Override
	public Consumer<ImmutableSession> preEvent(Void listener) {
		return Consumer.empty();
	}

	@Override
	public Consumer<ImmutableSession> postEvent(Void listener) {
		return Consumer.empty();
	}

	@Override
	public Void asEventListener(java.util.function.Consumer<ImmutableSession> preEvent, java.util.function.Consumer<ImmutableSession> postEvent) {
		return null;
	}
}
