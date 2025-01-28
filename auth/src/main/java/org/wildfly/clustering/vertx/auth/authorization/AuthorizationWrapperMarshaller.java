/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.auth.authorization;

import java.io.IOException;
import java.util.function.Function;

import io.vertx.ext.auth.authorization.AndAuthorization;
import io.vertx.ext.auth.authorization.Authorization;

import org.infinispan.protostream.descriptors.WireType;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;

/**
 * @author Paul Ferraro
 */
public class AuthorizationWrapperMarshaller<A extends Authorization> implements ProtoStreamMarshaller<A> {
	private static final int AUTHORIZATION_INDEX = 1;

	private final Function<Authorization, A> wrapper;
	private final Function<A, Authorization> unwrapper;

	public AuthorizationWrapperMarshaller(Function<Authorization, A> wrapper, Function<A, Authorization> unwrapper) {
		this.wrapper = wrapper;
		this.unwrapper = unwrapper;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<? extends A> getJavaClass() {
		return (Class<A>) this.wrapper.apply(AndAuthorization.create()).getClass().asSubclass(Authorization.class);
	}

	@Override
	public A readFrom(ProtoStreamReader reader) throws IOException {
		Authorization authorization = null;
		while (!reader.isAtEnd()) {
			int tag = reader.readTag();
			switch (WireType.getTagFieldNumber(tag)) {
				case AUTHORIZATION_INDEX:
					authorization = reader.readObject(Authorization.class);
					break;
				default:
					reader.skipField(tag);
			}
		}
		return this.wrapper.apply(authorization);
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, A authorization) throws IOException {
		Authorization wrapped = this.unwrapper.apply(authorization);
		if (wrapped != null) {
			writer.writeAny(AUTHORIZATION_INDEX, wrapped);
		}
	}
}
