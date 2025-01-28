/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.arquillian;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.kohsuke.MetaInfServices;

/**
 * @author Paul Ferraro
 */
@MetaInfServices(LoadableExtension.class)
public class VertxWebExtension implements LoadableExtension {

	@Override
	public void register(ExtensionBuilder builder) {
		builder.service(DeployableContainer.class, VertxWebContainer.class);
	}
}
