/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.auth;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.Authorization;

import org.infinispan.protostream.descriptors.WireType;
import org.wildfly.clustering.function.Function;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;
import org.wildfly.clustering.marshalling.protostream.util.StringKeyMapEntry;

/**
 * @author Paul Ferraro
 *
 */
public class UserMarshaller implements ProtoStreamMarshaller<User> {
	private static final int PRINCIPAL_INDEX = 1;
	private static final int AUTHORIZATION_ENTRY_INDEX = 2;
	private static final int ATTRIBUTES_INDEX = 3;

	@Override
	public Class<? extends User> getJavaClass() {
		return User.fromName("").getClass().asSubclass(User.class);
	}

	@Override
	public User readFrom(ProtoStreamReader reader) throws IOException {
		JsonObject principal = new JsonObject();
		List<Map.Entry<String, Set<Authorization>>> authorizations = new LinkedList<>();
		JsonObject attributes = new JsonObject();
		while (!reader.isAtEnd()) {
			int tag = reader.readTag();
			switch (WireType.getTagFieldNumber(tag)) {
				case PRINCIPAL_INDEX:
					principal = reader.readObject(JsonObject.class);
					break;
				case AUTHORIZATION_ENTRY_INDEX:
					Map.Entry<String, Set<Authorization>> entry = reader.readObject(StringKeyMapEntry.class);
					authorizations.add(entry);
					break;
				case ATTRIBUTES_INDEX:
					attributes = reader.readObject(JsonObject.class);
					break;
				default:
					reader.skipField(tag);
			}
		}
		User user = User.create(principal, attributes);
		for (Map.Entry<String, Set<Authorization>> entry : authorizations) {
			user.authorizations().put(entry.getKey(), entry.getValue());
		}
		return user;
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, User user) throws IOException {
		JsonObject principal = user.principal();
		if (!principal.isEmpty()) {
			writer.writeObject(PRINCIPAL_INDEX, principal);
		}
		Map<String, Set<Authorization>> authorizations = new TreeMap<>();
		user.authorizations().forEach((provider, authorization) -> authorizations.computeIfAbsent(provider, Function.get(HashSet::new)).add(authorization));
		for (Map.Entry<String, Set<Authorization>> entry : authorizations.entrySet()) {
			writer.writeObject(AUTHORIZATION_ENTRY_INDEX, new StringKeyMapEntry<>(entry.getKey(), entry.getValue()));
		}
		JsonObject attributes = user.attributes();
		if (!attributes.isEmpty()) {
			writer.writeObject(ATTRIBUTES_INDEX, attributes);
		}
	}
}
