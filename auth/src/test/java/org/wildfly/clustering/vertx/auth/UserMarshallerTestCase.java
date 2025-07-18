/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.auth;

import java.util.Map;
import java.util.Set;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.AndAuthorization;
import io.vertx.ext.auth.authorization.NotAuthorization;
import io.vertx.ext.auth.authorization.OrAuthorization;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.auth.authorization.WildcardPermissionBasedAuthorization;

import org.junit.jupiter.params.ParameterizedTest;
import org.wildfly.clustering.marshalling.Tester;
import org.wildfly.clustering.marshalling.TesterFactory;
import org.wildfly.clustering.marshalling.junit.TesterFactorySource;

/**
 * @author Paul Ferraro
 *
 */
public class UserMarshallerTestCase {

	@ParameterizedTest
	@TesterFactorySource
	public void test(TesterFactory factory) {
		Tester<User> tester = factory.createTester();
		tester.accept(User.fromName("foo"));
		tester.accept(User.fromToken("bar"));
		tester.accept(User.create(new JsonObject(Map.of("foo", "bar"))));

		User user = User.create(new JsonObject(Map.of("foo", "bar")), new JsonObject(Map.of("baz", "qux")));
		tester.accept(user);

		PermissionBasedAuthorization permission = PermissionBasedAuthorization.create("privileged");
		RoleBasedAuthorization role1 = RoleBasedAuthorization.create("admin");
		RoleBasedAuthorization role2 = RoleBasedAuthorization.create("user");
		WildcardPermissionBasedAuthorization wildcard = WildcardPermissionBasedAuthorization.create("*");
		user.authorizations().put("permissions", Set.of(permission, wildcard));
		user.authorizations().put("roles", Set.of(role1, role2));
		user.authorizations().put("not", NotAuthorization.create(permission));
		user.authorizations().put("and", AndAuthorization.create().addAuthorization(role1).addAuthorization(role2));
		user.authorizations().put("or", OrAuthorization.create().addAuthorization(permission).addAuthorization(wildcard));
		tester.accept(user);
	}
}
