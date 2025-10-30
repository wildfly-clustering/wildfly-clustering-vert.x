/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.handler;

import io.vertx.ext.web.handler.impl.UserHolder;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.protostream.AbstractSerializationContextInitializer;
import org.wildfly.clustering.marshalling.protostream.SerializationContext;
import org.wildfly.clustering.marshalling.protostream.SerializationContextInitializer;

/**
 * The serialization context initializer for the {@link io.vertx.ext.web.handler.impl} package.
 * @author Paul Ferraro
 */
@MetaInfServices(SerializationContextInitializer.class)
public class VertxHandlerSerializationContextInitializer extends AbstractSerializationContextInitializer {
	/**
	 * Creates a serializaation context initializer.
	 */
	public VertxHandlerSerializationContextInitializer() {
		super(UserHolder.class.getPackage());
	}

	@Override
	public void registerMarshallers(SerializationContext context) {
		context.registerMarshaller(UserHolderMarshaller.INSTANCE);
	}
}
