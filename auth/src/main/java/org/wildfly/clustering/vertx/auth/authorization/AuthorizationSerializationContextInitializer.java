/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.auth.authorization;

import io.vertx.ext.auth.authorization.AndAuthorization;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.NotAuthorization;
import io.vertx.ext.auth.authorization.OrAuthorization;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.auth.authorization.WildcardPermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.impl.AuthorizationsImpl;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.protostream.AbstractSerializationContextInitializer;
import org.wildfly.clustering.marshalling.protostream.Scalar;
import org.wildfly.clustering.marshalling.protostream.SerializationContext;
import org.wildfly.clustering.marshalling.protostream.SerializationContextInitializer;

/**
 * The serialization context initializer for the {@link io.vertx.ext.auth.authorization} package.
 * @author Paul Ferraro
 */
@MetaInfServices(SerializationContextInitializer.class)
public class AuthorizationSerializationContextInitializer extends AbstractSerializationContextInitializer {
	/**
	 * Creates a serialization context initializer.
	 */
	public AuthorizationSerializationContextInitializer() {
		super(AuthorizationsImpl.class.getPackage());
	}

	@Override
	public void registerMarshallers(SerializationContext context) {
		context.registerMarshaller(new AuthorizationListMarshaller<>(AndAuthorization::create, AndAuthorization::addAuthorization, AndAuthorization::getAuthorizations));
		context.registerMarshaller(new AuthorizationListMarshaller<>(OrAuthorization::create, OrAuthorization::addAuthorization, OrAuthorization::getAuthorizations));
		context.registerMarshaller(Scalar.ANY.cast(Authorization.class).toMarshaller(NotAuthorization.create(AndAuthorization.create()).getClass().asSubclass(NotAuthorization.class), NotAuthorization::getAuthorization, NotAuthorization::create));
		context.registerMarshaller(Scalar.STRING.cast(String.class).toMarshaller(PermissionBasedAuthorization.create("").getClass().asSubclass(PermissionBasedAuthorization.class), PermissionBasedAuthorization::getPermission, PermissionBasedAuthorization::create));
		context.registerMarshaller(Scalar.STRING.cast(String.class).toMarshaller(RoleBasedAuthorization.create("").getClass().asSubclass(RoleBasedAuthorization.class), RoleBasedAuthorization::getRole, RoleBasedAuthorization::create));
		context.registerMarshaller(Scalar.STRING.cast(String.class).toMarshaller(WildcardPermissionBasedAuthorization.create("*").getClass().asSubclass(WildcardPermissionBasedAuthorization.class), WildcardPermissionBasedAuthorization::getPermission, WildcardPermissionBasedAuthorization::create));
	}
}
