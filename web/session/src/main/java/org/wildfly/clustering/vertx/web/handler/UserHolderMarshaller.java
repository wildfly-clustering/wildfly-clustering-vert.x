/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.handler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.UserContext;
import io.vertx.ext.web.handler.impl.UserHolder;
import io.vertx.ext.web.impl.UserContextImpl;
import io.vertx.ext.web.impl.UserContextInternal;

import org.infinispan.protostream.descriptors.WireType;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;

/**
 * @author Paul Ferraro
 */
public class UserHolderMarshaller implements ProtoStreamMarshaller<UserHolder> {
	private static final int USER_INDEX = 1;

	// Yuck - user/context fields are not accessible!!!
	private static final Field CONTEXT_FIELD = findField(RoutingContext.class);
	private static final Field USER_FIELD = findField(User.class);

	@Override
	public Class<? extends UserHolder> getJavaClass() {
		return UserHolder.class;
	}

	@Override
	public UserHolder readFrom(ProtoStreamReader reader) throws IOException {
		User user = null;
		while (!reader.isAtEnd()) {
			int tag = reader.readTag();
			switch (WireType.getTagFieldNumber(tag)) {
				case USER_INDEX:
					user = reader.readAny(User.class);
					break;
				default:
					reader.skipField(tag);
			}
		}
		return new UserHolder(new UserRoutingContext(user));
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, UserHolder holder) throws IOException {
		try {
			RoutingContext context = (RoutingContext) CONTEXT_FIELD.get(holder);
			User user = context.user();
			if (user == null) {
				user = (User) USER_FIELD.get(holder);
			}
			writer.writeAny(USER_INDEX, user);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field findField(Class<?> type) {
		for (Field field : UserHolder.class.getDeclaredFields()) {
			if (field.getType() == type) {
				field.setAccessible(true);
				return field;
			}
		}
		throw new IllegalArgumentException(type.getName());
	}

	private static class UserRoutingContext implements RoutingContext {
		private volatile UserContextInternal context = new UserContextImpl(this);

		UserRoutingContext(User user) {
			this.context.setUser(user);
		}

		@Override
		public UserContext userContext() {
			return this.context;
		}

		@Override
		public HttpServerRequest request() {
			throw new UnsupportedOperationException();
		}

		@Override
		public HttpServerResponse response() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void next() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void fail(int statusCode) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void fail(Throwable throwable) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void fail(int statusCode, Throwable throwable) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RoutingContext put(String key, Object obj) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T get(String key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T get(String key, T defaultValue) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T remove(String key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, Object> data() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Vertx vertx() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String mountPoint() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Route currentRoute() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String normalizedPath() {
			throw new UnsupportedOperationException();
		}

		@Override
		public RequestBody body() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<FileUpload> fileUploads() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void cancelAndCleanupFileUploads() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Session session() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isSessionAccessed() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Throwable failure() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int statusCode() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getAcceptableContentType() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ParsedHeaderValues parsedHeaders() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int addHeadersEndHandler(Handler<Void> handler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeHeadersEndHandler(int handlerID) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int addBodyEndHandler(Handler<Void> handler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeBodyEndHandler(int handlerID) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int addEndHandler(Handler<AsyncResult<Void>> handler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeEndHandler(int handlerID) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean failed() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setAcceptableContentType(String contentType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void reroute(HttpMethod method, String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, String> pathParams() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String pathParam(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public MultiMap queryParams() {
			throw new UnsupportedOperationException();
		}

		@Override
		public MultiMap queryParams(Charset encoding) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<String> queryParam(String name) {
			throw new UnsupportedOperationException();
		}
	}
}
