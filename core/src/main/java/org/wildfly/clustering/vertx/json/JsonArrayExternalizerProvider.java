/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.json;

import io.vertx.core.json.JsonArray;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.jboss.ExternalizerProvider;

/**
 * {@link JsonArray} externalizer provider for JBoss Marshalling.
 * @author Paul Ferraro
 */
@MetaInfServices(ExternalizerProvider.class)
public class JsonArrayExternalizerProvider extends ClusterSerializableExternalizerProvider {
	private static final long serialVersionUID = -6454486110962842453L;

	/**
	 * Creates an externalizer provider.
	 */
	public JsonArrayExternalizerProvider() {
		super(JsonArray.class);
	}
}
