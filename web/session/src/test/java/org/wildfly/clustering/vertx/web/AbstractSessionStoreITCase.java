/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import io.vertx.ext.web.handler.SessionHandler;

import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.wildfly.clustering.session.container.AbstractSessionManagerITCase;
import org.wildfly.clustering.session.container.SessionManagementTesterConfiguration;
import org.wildfly.clustering.vertx.web.arquillian.RouterConfigurator;
import org.wildfly.clustering.vertx.web.routes.SessionRouterConfigurator;

/**
 * @author Paul Ferraro
 */
public class AbstractSessionStoreITCase extends AbstractSessionManagerITCase<JavaArchive> {

	protected AbstractSessionStoreITCase() {
		super(new SessionManagementTesterConfiguration() {
			@Override
			public Class<?> getEndpointClass() {
				return SessionHandler.class;
			}

			@Override
			public boolean isNullableSession() {
				return false;
			}
		}, JavaArchive.class);
	}

	@Override
	public JavaArchive createArchive(org.wildfly.clustering.session.container.SessionManagementTesterConfiguration configuration) {
		JavaArchive archive = super.createArchive(configuration);
		archive.addClass(RouterConfigurator.class);
		archive.addPackage(SessionRouterConfigurator.class.getPackage());
		archive.addAsServiceProvider(RouterConfigurator.class, SessionRouterConfigurator.class);
		return archive;
	}
}
