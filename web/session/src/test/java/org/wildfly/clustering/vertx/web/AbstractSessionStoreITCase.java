/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import io.vertx.ext.web.handler.SessionHandler;

import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.wildfly.clustering.session.container.AbstractSessionManagerITCase;
import org.wildfly.clustering.session.container.SessionManagementTesterConfiguration;
import org.wildfly.clustering.vertx.web.arquillian.RouterConfigurator;
import org.wildfly.clustering.vertx.web.routes.SessionRouterConfigurator;

/**
 * @author Paul Ferraro
 * @param <A> the test arguments type
 */
public abstract class AbstractSessionStoreITCase<A extends SessionManagementArguments> extends AbstractSessionManagerITCase<A, JavaArchive> {

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
	public JavaArchive createArchive(A arguments) {
		System.out.println(Map.copyOf(arguments.getManifest().getMainAttributes()));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			arguments.getManifest().write(output);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return super.createArchive(arguments)
				.addClass(RouterConfigurator.class)
				.addPackage(SessionRouterConfigurator.class.getPackage())
				.addAsServiceProvider(RouterConfigurator.class, SessionRouterConfigurator.class)
				.setManifest(new ByteArrayAsset(output.toByteArray()));
	}
}
