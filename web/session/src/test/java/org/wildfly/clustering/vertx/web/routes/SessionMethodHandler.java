/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.routes;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;

import org.jboss.logging.Logger;
import org.wildfly.clustering.session.container.SessionManagementEndpointConfiguration;

/**
 * @author Paul Ferraro
 */
public enum SessionMethodHandler implements Handler<RoutingContext>, BiConsumer<Session, HttpServerResponse>, Supplier<HttpMethod> {

	HEAD(HttpMethod.HEAD) {
		@Override
		public void accept(Session session, HttpServerResponse response) {
			recordSession(session, response, AtomicInteger::get);
		}
	},
	GET(HttpMethod.GET) {
		@Override
		public void accept(Session session, HttpServerResponse response) {
			recordSession(session, response, AtomicInteger::incrementAndGet);
		}
	},
	PUT(HttpMethod.PUT) {
		@Override
		public void accept(Session session, HttpServerResponse response) {
			UUID immutableValue = UUID.randomUUID();
			session.put(SessionManagementEndpointConfiguration.IMMUTABLE, immutableValue);
			response.putHeader(SessionManagementEndpointConfiguration.IMMUTABLE, immutableValue.toString());

			AtomicInteger counter = new AtomicInteger(0);
			session.put(SessionManagementEndpointConfiguration.COUNTER, counter);
			response.putHeader(SessionManagementEndpointConfiguration.COUNTER, String.valueOf(counter.get()));
		}
	},
	DELETE(HttpMethod.DELETE) {
		@Override
		public void accept(Session session, HttpServerResponse response) {
			session.destroy();
		}
	},
	;
	private static final Logger LOGGER = Logger.getLogger(SessionMethodHandler.class);
	private final HttpMethod method;

	SessionMethodHandler(HttpMethod method) {
		this.method = method;
	}

	@Override
	public HttpMethod get() {
		return this.method;
	}

	@Override
	public void handle(RoutingContext context) {
		HttpServerRequest request = context.request();
		HttpServerResponse response = context.response();
		try {
			Session session = context.session();
			LOGGER.infof("[%s] %s;%s=%s", request.method(), request.absoluteURI(), SessionHandler.DEFAULT_SESSION_COOKIE_NAME, Optional.ofNullable(request.getCookie(SessionHandler.DEFAULT_SESSION_COOKIE_NAME)).map(Cookie::getValue).orElse(null));
			response.putHeader(SessionManagementEndpointConfiguration.SESSION_ID, session.id());
			this.accept(session, response);
			if (!response.headWritten()) {
				response.putHeader(SessionManagementEndpointConfiguration.SESSION_ID, session.isDestroyed() ? null : session.id());
			}
		} finally {
			context.end();
		}
	}

	private static void recordSession(Session session, HttpServerResponse response, ToIntFunction<AtomicInteger> count) {
		AtomicInteger counter = session.get(SessionManagementEndpointConfiguration.COUNTER);
		if (counter != null) {
			response.putHeader(SessionManagementEndpointConfiguration.COUNTER, String.valueOf(count.applyAsInt(counter)));
		}
		UUID value = session.get(SessionManagementEndpointConfiguration.IMMUTABLE);
		if (value != null) {
			response.putHeader(SessionManagementEndpointConfiguration.IMMUTABLE, value.toString());
		}
	}
}
