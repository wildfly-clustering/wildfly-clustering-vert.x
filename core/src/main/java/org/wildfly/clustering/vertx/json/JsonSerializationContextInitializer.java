/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.json;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.protostream.AbstractSerializationContextInitializer;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.SerializationContext;
import org.wildfly.clustering.marshalling.protostream.SerializationContextInitializer;

/**
 * @author Paul Ferraro
 */
@MetaInfServices(SerializationContextInitializer.class)
public class JsonSerializationContextInitializer extends AbstractSerializationContextInitializer {

	public JsonSerializationContextInitializer() {
		super(JsonObject.class.getPackage());
	}

	@Override
	public void registerMarshallers(SerializationContext context) {
		context.registerMarshaller(new JsonObjectMarshaller());
		@SuppressWarnings("unchecked")
		ProtoStreamMarshaller<List<Object>> listMarshaller = (ProtoStreamMarshaller<List<Object>>) (ProtoStreamMarshaller<?>) context.getMarshaller(ArrayList.class);
		context.registerMarshaller(listMarshaller.wrap(JsonArray.class, JsonArray::getList, JsonArray::new));
	}
}
