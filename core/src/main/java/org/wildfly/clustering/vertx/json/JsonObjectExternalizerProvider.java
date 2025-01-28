/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.json;

import io.vertx.core.json.JsonObject;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.jboss.ExternalizerProvider;

/**
 * {@link JsonObject} externalizer provider for JBoss Marshalling.
 * @author Paul Ferraro
 */
@MetaInfServices(ExternalizerProvider.class)
public class JsonObjectExternalizerProvider extends ClusterSerializableExternalizerProvider {
	private static final long serialVersionUID = 5631247217115998338L;

	public JsonObjectExternalizerProvider() {
		super(JsonObject.class);
	}
}
