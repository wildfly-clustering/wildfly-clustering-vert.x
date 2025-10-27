/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.arquillian;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.kohsuke.MetaInfServices;

/**
 * An Arquillian extension that enables deployment to a Vert.x Web container.
 * @author Paul Ferraro
 */
@MetaInfServices(LoadableExtension.class)
public class VertxWebExtension implements LoadableExtension {
	/**
	 * Creates a new extension.
	 */
	public VertxWebExtension() {
	}

	@Override
	public void register(ExtensionBuilder builder) {
		builder.service(DeployableContainer.class, VertxWebContainer.class);
	}
}
