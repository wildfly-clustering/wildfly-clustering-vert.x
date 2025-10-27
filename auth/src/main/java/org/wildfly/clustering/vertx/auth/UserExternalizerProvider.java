/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.auth;

import io.vertx.ext.auth.User;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.jboss.ExternalizerProvider;
import org.wildfly.clustering.vertx.json.ClusterSerializableExternalizerProvider;

/**
 * A provider of a User externalizer/
 * @author Paul Ferraro
 */
@MetaInfServices(ExternalizerProvider.class)
public class UserExternalizerProvider extends ClusterSerializableExternalizerProvider {
	private static final long serialVersionUID = -8302938940277265639L;

	/**
	 * Create an externalizer provider.
	 */
	public UserExternalizerProvider() {
		super(User.fromName("").getClass());
	}
}
