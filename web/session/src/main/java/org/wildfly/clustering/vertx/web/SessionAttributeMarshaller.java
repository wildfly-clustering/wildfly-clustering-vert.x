/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.io.ObjectInputFilter;
import java.util.Optional;
import java.util.function.Function;

import org.wildfly.clustering.marshalling.ByteBufferMarshaller;
import org.wildfly.clustering.marshalling.java.JavaByteBufferMarshaller;
import org.wildfly.clustering.marshalling.jboss.JBossByteBufferMarshaller;
import org.wildfly.clustering.marshalling.jboss.MarshallingConfigurationRepository;
import org.wildfly.clustering.marshalling.protostream.ClassLoaderMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamByteBufferMarshaller;
import org.wildfly.clustering.marshalling.protostream.SerializationContextBuilder;

/**
 * @author Paul Ferraro
 */
public enum SessionAttributeMarshaller implements Function<ClassLoader, ByteBufferMarshaller> {

	JAVA() {
		@Override
		public ByteBufferMarshaller apply(ClassLoader loader) {
			ObjectInputFilter filter = Optional.ofNullable(System.getProperty("jdk.serialFilter")).map(ObjectInputFilter.Config::createFilter).orElse(null);
			return new JavaByteBufferMarshaller(loader, filter);
		}
	},
	JBOSS() {
		@Override
		public ByteBufferMarshaller apply(ClassLoader loader) {
			return new JBossByteBufferMarshaller(MarshallingConfigurationRepository.from(JBossMarshallingVersion.CURRENT, loader), loader);
		}
	},
	PROTOSTREAM() {
		@Override
		public ByteBufferMarshaller apply(ClassLoader loader) {
			return new ProtoStreamByteBufferMarshaller(SerializationContextBuilder.newInstance(ClassLoaderMarshaller.of(loader)).load(loader).build());
		}
	},
	;
}
