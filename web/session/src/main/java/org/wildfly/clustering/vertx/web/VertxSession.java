/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import io.vertx.ext.web.Session;

/**
 * A closeable Vert.x session
 * @author Paul Ferraro
 */
public interface VertxSession extends Session, AutoCloseable {

	@Override
	default void setAccessed() {
		this.close();
	}

	@Override
	void close();
}
