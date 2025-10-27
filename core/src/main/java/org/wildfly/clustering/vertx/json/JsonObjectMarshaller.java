/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.json;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import io.vertx.core.json.JsonObject;

import org.infinispan.protostream.descriptors.WireType;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;
import org.wildfly.clustering.marshalling.protostream.util.StringKeyMapEntry;

/**
 * A ProtoStream marshaller for a JsonObject, optimized for string keys.
 * @author Paul Ferraro
 */
public enum JsonObjectMarshaller implements ProtoStreamMarshaller<JsonObject> {
	/** Singleton instance */
	INSTANCE;
	private static final int ENTRY_INDEX = 1;

	@Override
	public Class<? extends JsonObject> getJavaClass() {
		return JsonObject.class;
	}

	@Override
	public JsonObject readFrom(ProtoStreamReader reader) throws IOException {
		Map<String, Object> map = new TreeMap<>();
		while (!reader.isAtEnd()) {
			int tag = reader.readTag();
			switch (WireType.getTagFieldNumber(tag)) {
				case ENTRY_INDEX:
					Map.Entry<String, Object> entry = reader.readObject(StringKeyMapEntry.class);
					map.put(entry.getKey(), entry.getValue());
					break;
				default:
					reader.skipField(tag);
			}
		}
		return new JsonObject(map);
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, JsonObject object) throws IOException {
		for (Map.Entry<String, Object> entry : object.getMap().entrySet()) {
			writer.writeObject(ENTRY_INDEX, new StringKeyMapEntry<>(entry.getKey(), entry.getValue()));
		}
	}
}
