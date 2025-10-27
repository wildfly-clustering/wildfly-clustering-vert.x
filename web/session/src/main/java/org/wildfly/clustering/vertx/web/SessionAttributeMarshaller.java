/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.io.ObjectInputFilter;
import java.util.Optional;
import java.util.function.Function;

import org.jboss.marshalling.SimpleClassResolver;
import org.wildfly.clustering.marshalling.ByteBufferMarshaller;
import org.wildfly.clustering.marshalling.java.JavaByteBufferMarshaller;
import org.wildfly.clustering.marshalling.jboss.JBossByteBufferMarshaller;
import org.wildfly.clustering.marshalling.jboss.MarshallingConfigurationBuilder;
import org.wildfly.clustering.marshalling.protostream.ClassLoaderMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamByteBufferMarshaller;
import org.wildfly.clustering.marshalling.protostream.SerializationContextBuilder;

/**
 * Enumerates the supported session attribute marshallers.
 * @author Paul Ferraro
 */
public enum SessionAttributeMarshaller implements Function<ClassLoader, ByteBufferMarshaller> {
	/** Creates a marshaller based on JDK serialization. */
	JAVA() {
		@Override
		public ByteBufferMarshaller apply(ClassLoader loader) {
			ObjectInputFilter filter = Optional.ofNullable(System.getProperty("jdk.serialFilter")).map(ObjectInputFilter.Config::createFilter).orElse(null);
			return new JavaByteBufferMarshaller(loader, filter);
		}
	},
	/** Creates a marshaller based on JBoss Marshalling. */
	JBOSS() {
		@Override
		public ByteBufferMarshaller apply(ClassLoader loader) {
			return new JBossByteBufferMarshaller(MarshallingConfigurationBuilder.newInstance(new SimpleClassResolver(loader)).load(loader).build(), loader);
		}
	},
	/** Creates a marshaller based on ProtoStream. */
	PROTOSTREAM() {
		@Override
		public ByteBufferMarshaller apply(ClassLoader loader) {
			return new ProtoStreamByteBufferMarshaller(SerializationContextBuilder.newInstance(ClassLoaderMarshaller.of(loader)).load(loader).build());
		}
	},
	;
}
