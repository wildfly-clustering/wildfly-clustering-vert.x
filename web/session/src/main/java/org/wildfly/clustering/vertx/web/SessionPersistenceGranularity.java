/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.util.function.Supplier;

import org.wildfly.clustering.session.SessionAttributePersistenceStrategy;

/**
 * Enumerates the supported strategies for persisting session attributes.
 * @author Paul Ferraro
 */
public enum SessionPersistenceGranularity implements Supplier<SessionAttributePersistenceStrategy> {
	/** A strategy that always persists all attributes of a session, where any shared object references between attributes are preserved. */
	SESSION(SessionAttributePersistenceStrategy.COARSE),
	/** A strategy that only persists modified/mutable attributes of a session, where any shared object references between attributes are not preserved. */
	ATTRIBUTE(SessionAttributePersistenceStrategy.FINE),
	;
	private final SessionAttributePersistenceStrategy strategy;

	SessionPersistenceGranularity(SessionAttributePersistenceStrategy strategy) {
		this.strategy = strategy;
	}

	@Override
	public SessionAttributePersistenceStrategy get() {
		return this.strategy;
	}
}
