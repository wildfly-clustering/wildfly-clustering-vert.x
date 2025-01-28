/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import io.vertx.ext.web.handler.SessionHandler;

import org.junit.jupiter.api.Test;
import org.wildfly.clustering.session.IdentifierMarshaller;

/**
 * @author Paul Ferraro
 *
 */
public class VertxSessionIdentifierFactoryTestCase {

	@Test
	public void test() throws IOException {
		Vertx vertx = Vertx.vertx();
		try {
			VertxSessionIdentifierFactory factory = new VertxSessionIdentifierFactory(vertx.getOrCreateContext());
			String id = factory.get();
			System.out.println(id);
			// Verify that our generated session ID will be accepted by SessionHandlerImpl.
			assertThat(id.length()).isGreaterThan(SessionHandler.DEFAULT_SESSIONID_MIN_LENGTH);
			ByteBuffer buffer = IdentifierMarshaller.HEX.write(id);
			assertThat(IdentifierMarshaller.HEX.read(buffer)).isEqualTo(id);
		} finally {
			CompletableFuture<Void> future = new CompletableFuture<>();
			vertx.close().onComplete(future::complete, future::completeExceptionally);
			future.join();
		}
	}
}
