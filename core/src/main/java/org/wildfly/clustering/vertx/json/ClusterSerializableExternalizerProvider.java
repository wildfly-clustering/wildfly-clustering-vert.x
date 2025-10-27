/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.json;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.ClusterSerializable;

import org.jboss.marshalling.Externalizer;
import org.wildfly.clustering.marshalling.IndexSerializer;
import org.wildfly.clustering.marshalling.jboss.ExternalizerProvider;

/**
 * Generic externalizer provider for JBoss Marshalling for use with {@link ClusterSerializable} implementations.
 * @author Paul Ferraro
 */
public class ClusterSerializableExternalizerProvider implements ExternalizerProvider, Externalizer {
	private static final long serialVersionUID = -4684446380186460640L;
	/** The constructor on the serialization type */
	private final Constructor<?> constructor;

	/**
	 * Creates an externalizer provider for the specified type.
	 * @param type the target type
	 */
	public ClusterSerializableExternalizerProvider(Class<?> type) {
		try {
			this.constructor = type.getConstructor();
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(type.getCanonicalName());
		}
	}

	@Override
	public Class<?> getType() {
		return this.constructor.getDeclaringClass();
	}

	@Override
	public void writeExternal(Object subject, ObjectOutput output) throws IOException {
		ClusterSerializable serializable = (ClusterSerializable) subject;
		Buffer buffer = Buffer.buffer();
		serializable.writeToBuffer(buffer);
		IndexSerializer.VARIABLE.writeInt(output, buffer.length());
		for (int i = 0; i < buffer.length(); ++i) {
			output.writeByte(buffer.getByte(i));
		}
	}

	@Override
	public Object createExternal(Class<?> subjectType, ObjectInput input) throws IOException, ClassNotFoundException {
		try {
			ClusterSerializable serializable = (ClusterSerializable) this.constructor.newInstance();
			int size = IndexSerializer.VARIABLE.readInt(input);
			Buffer buffer = Buffer.buffer(size);
			for (int i = 0; i < size; ++i) {
				buffer.appendByte(input.readByte());
			}
			serializable.readFromBuffer(0, buffer);
			return serializable;
		} catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
			throw new ClassNotFoundException(e.getLocalizedMessage(), e);
		}
	}

	@Override
	public Externalizer getExternalizer() {
		return this;
	}
}
