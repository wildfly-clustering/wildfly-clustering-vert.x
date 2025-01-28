/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.json;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.junit.jupiter.params.ParameterizedTest;
import org.wildfly.clustering.marshalling.Tester;
import org.wildfly.clustering.marshalling.TesterFactory;
import org.wildfly.clustering.marshalling.junit.TesterFactorySource;

/**
 * @author Paul Ferraro
 */
public class JsonMarshallingTestCase {

	@ParameterizedTest
	@TesterFactorySource
	public void testArray(TesterFactory factory) {
		Tester<JsonArray> tester = factory.createTester();
		JsonArray array = new JsonArray();
		tester.accept(new JsonArray());
		array.add("string");
		array.add(1);
		array.add(100L);
		// JsonArray.equals(...) is buggy and confuses float vs double.
//		array.add(0.1f);
		array.add(0.01d);
		array.add("foo".getBytes());
		array.add(true);
		array.addNull();
		array.add(Instant.now());
		array.add(new JsonObject(Map.of("foo", "bar")));
		array.add(new JsonArray(List.of("baz", "qux")));
		tester.accept(array);
	}

	@ParameterizedTest
	@TesterFactorySource
	public void testObject(TesterFactory factory) {
		Tester<JsonObject> tester = factory.createTester();
		JsonObject object = new JsonObject();
		tester.accept(object);
		object.put("string", "foo");
		object.put("int", 1);
		object.put("long", 100L);
		object.put("float", 1.0f);
		object.put("double", 1.0d);
		object.put("bytes", "foo".getBytes());
		object.put("boolean", true);
		object.putNull("null");
		object.put("instant", Instant.now());
		object.put("object", new JsonObject(Map.of("foo", "bar")));
		object.put("array", new JsonArray(List.of("baz", "qux")));
		tester.accept(object);
	}
}
