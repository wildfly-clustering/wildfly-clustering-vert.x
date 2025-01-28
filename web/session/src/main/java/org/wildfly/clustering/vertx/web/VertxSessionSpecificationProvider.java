/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.util.function.Consumer;

import io.vertx.core.Context;

import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.spec.SessionEventListenerSpecificationProvider;
import org.wildfly.clustering.session.spec.SessionSpecificationProvider;
import org.wildfly.common.function.Functions;

public enum VertxSessionSpecificationProvider implements SessionSpecificationProvider<ImmutableSession, Context>, SessionEventListenerSpecificationProvider<ImmutableSession, Void> {
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
		return Functions.discardingConsumer();
	}

	@Override
	public Consumer<ImmutableSession> postEvent(Void listener) {
		return Functions.discardingConsumer();
	}

	@Override
	public Void asEventListener(Consumer<ImmutableSession> preEvent, Consumer<ImmutableSession> postEvent) {
		return null;
	}
}
