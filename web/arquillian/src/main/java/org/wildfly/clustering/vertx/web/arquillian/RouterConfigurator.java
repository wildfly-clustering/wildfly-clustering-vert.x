/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.arquillian;

import java.util.function.Consumer;

import io.vertx.ext.web.Router;

/**
 * Configures the router for a Vert.x deployment.
 * @author Paul Ferraro
 */
public interface RouterConfigurator extends Consumer<Router> {
}
