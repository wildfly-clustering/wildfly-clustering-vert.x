/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.auth;

import io.vertx.ext.auth.User;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.protostream.AbstractSerializationContextInitializer;
import org.wildfly.clustering.marshalling.protostream.SerializationContext;
import org.wildfly.clustering.marshalling.protostream.SerializationContextInitializer;

/**
 * @author Paul Ferraro
 */
@MetaInfServices(SerializationContextInitializer.class)
public class UserSerializationContextInitializer extends AbstractSerializationContextInitializer {

	public UserSerializationContextInitializer() {
		super(User.fromName("").getClass().getPackage());
	}

	@Override
	public void registerMarshallers(SerializationContext context) {
		context.registerMarshaller(new UserMarshaller());
	}
}
