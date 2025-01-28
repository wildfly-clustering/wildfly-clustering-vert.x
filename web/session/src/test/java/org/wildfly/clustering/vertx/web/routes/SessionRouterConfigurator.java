/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.routes;

import java.util.EnumSet;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.session.container.SessionManagementEndpointConfiguration;
import org.wildfly.clustering.vertx.web.arquillian.RouterConfigurator;

/**
 * @author Paul Ferraro
 */
@MetaInfServices(RouterConfigurator.class)
public class SessionRouterConfigurator implements RouterConfigurator {

	@Override
	public void accept(Router router) {
		for (SessionMethodHandler handler : EnumSet.allOf(SessionMethodHandler.class)) {
			HttpMethod method = handler.get();
			router.route(method, SessionManagementEndpointConfiguration.ENDPOINT_PATH).setName(method.name()).handler(handler);
		}
	}
}
