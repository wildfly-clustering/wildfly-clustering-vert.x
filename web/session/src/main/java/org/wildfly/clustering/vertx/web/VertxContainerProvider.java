/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.util.Optional;

import io.vertx.core.Context;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.function.Consumer;
import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.SessionManager;
import org.wildfly.clustering.session.container.ContainerProvider;

/**
 * Provides a Vert.x Web container.
 * @author Paul Ferraro
 */
@MetaInfServices(ContainerProvider.class)
public class VertxContainerProvider implements ContainerProvider<Context, ImmutableSession, Void, Void> {

	/**
	 * Constructs a new vert.x session container provider.
	 */
	public VertxContainerProvider() {
	}

	@Override
	public String getId(Context context) {
		return context.deploymentID();
	}

	@Override
	public ImmutableSession getDetachableSession(SessionManager<Void> manager, ImmutableSession session, Context context) {
		return session;
	}

	@Override
	public ImmutableSession getDetachedSession(SessionManager<Void> manager, String id, Context context) {
		return manager.getDetachedSession(id);
	}

	@Override
	public Optional<Void> getSessionEventListener(ImmutableSession session, Object attribute) {
		return Optional.empty();
	}

	@Override
	public Consumer<ImmutableSession> getPrePassivateEventNotifier(Void listener) {
		return Consumer.empty();
	}

	@Override
	public Consumer<ImmutableSession> getPostActivateEventNotifier(Void listener) {
		return Consumer.empty();
	}

	@Override
	public String toString() {
		return "Vert.x 5.0 container";
	}
}
