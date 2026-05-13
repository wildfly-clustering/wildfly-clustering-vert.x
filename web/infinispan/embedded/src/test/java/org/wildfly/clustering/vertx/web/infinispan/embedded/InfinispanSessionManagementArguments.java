/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.vertx.web.infinispan.embedded;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.wildfly.clustering.vertx.web.SessionManagementArguments;

/**
 * @author Paul Ferraro
 */
public interface InfinispanSessionManagementArguments extends SessionManagementArguments {

	String getTemplate();

	@Override
	default Manifest getManifest() {
		Manifest manifest = SessionManagementArguments.super.getManifest();
		manifest.getMainAttributes().put(new Attributes.Name(InfinispanSessionStore.CACHE), this.getTemplate());
		return manifest;
	}
}
