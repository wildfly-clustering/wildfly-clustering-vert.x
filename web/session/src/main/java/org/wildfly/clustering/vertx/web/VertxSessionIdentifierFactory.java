/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.util.HexFormat;

import io.vertx.core.Context;
import io.vertx.ext.auth.prng.VertxContextPRNG;
import io.vertx.ext.web.handler.SessionHandler;

import org.wildfly.clustering.function.Supplier;

/**
 * A factory for creating Vert.x session identifiers.
 * @author Paul Ferraro
 */
public class VertxSessionIdentifierFactory implements Supplier<String> {
	/** The name of the property used to configure the session identifier length */
	public static final String SESSION_ID_LENGTH = "sessionIdentifierLength";

	private final VertxContextPRNG random;
	private int bytes;
	private final HexFormat hex = HexFormat.of().withLowerCase();

	/**
	 * Creates a session identifier factory.
	 * @param context the associated vert.x context
	 */
	public VertxSessionIdentifierFactory(Context context) {
		this.random = VertxContextPRNG.current(context);
		// N.B. The logic here replicates the behavior of io.vertx.ext.web.sstore.AbstractSession
		// The Vert.x javadoc for {@link SessionHandler#DEFAULT_SESSIONID_MIN_LENGTH} links to:
		// https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#session-id-length
		// This document recommends a minimum of 64 bits (i.e. 8 bytes) of entropy for a session identifier, represented by 16 hexadecimal characters.
		// However, SessionHandlerImpl requires the string length to exceed this length.
		// Therefore, the value specified via SessionHandler.setMinLength(...) is not actually a minimum length, but rather a maximum "insufficient length"
		// Consequently, for a session ID to be accepted by SessionHandlerImpl.handle(...), the default value of our sessionIdentifierLength must exceed SessionHandler.DEFAULT_SESSIONID_MIN_LENGTH
		this.bytes = context.config().getInteger(SESSION_ID_LENGTH, SessionHandler.DEFAULT_SESSIONID_MIN_LENGTH + 2) / 2;
	}

	@Override
	public String get() {
		byte[] bytes = new byte[this.bytes];
		this.random.nextBytes(bytes);
		return this.hex.formatHex(bytes);
	}
}
