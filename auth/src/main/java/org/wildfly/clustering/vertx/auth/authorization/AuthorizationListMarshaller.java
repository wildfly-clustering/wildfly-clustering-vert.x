/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.auth.authorization;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vertx.ext.auth.authorization.Authorization;

import org.infinispan.protostream.descriptors.WireType;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;

/**
 * @author Paul Ferraro
 *
 */
public class AuthorizationListMarshaller<A extends Authorization> implements ProtoStreamMarshaller<A> {
	private static final int AUTHORIZATION_INDEX = 1;

	private final Supplier<A> factory;
	private final BiConsumer<A, Authorization> appender;
	private final Function<A, List<Authorization>> authorizations;

	public AuthorizationListMarshaller(Supplier<A> factory, BiConsumer<A, Authorization> appender, Function<A, List<Authorization>> authorizations) {
		this.factory = factory;
		this.appender = appender;
		this.authorizations = authorizations;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<? extends A> getJavaClass() {
		return (Class<A>) this.factory.get().getClass();
	}

	@Override
	public A readFrom(ProtoStreamReader reader) throws IOException {
		A authorization = this.factory.get();
		while (!reader.isAtEnd()) {
			int tag = reader.readTag();
			switch (WireType.getTagFieldNumber(tag)) {
				case AUTHORIZATION_INDEX:
					this.appender.accept(authorization, reader.readAny(Authorization.class));
					break;
				default:
					reader.skipField(tag);
			}
		}
		return authorization;
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, A authorization) throws IOException {
		for (Authorization auth : this.authorizations.apply(authorization)) {
			writer.writeAny(AUTHORIZATION_INDEX, auth);
		}
	}
}
