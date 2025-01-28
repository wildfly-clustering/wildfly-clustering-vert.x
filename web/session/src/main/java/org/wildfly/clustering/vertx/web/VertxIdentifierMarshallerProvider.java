/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.nio.ByteBuffer;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.Marshaller;
import org.wildfly.clustering.session.IdentifierMarshaller;
import org.wildfly.clustering.session.IdentifierMarshallerProvider;

/**
 * @author Paul Ferraro
 */
@MetaInfServices(IdentifierMarshallerProvider.class)
public class VertxIdentifierMarshallerProvider implements IdentifierMarshallerProvider {

	@Override
	public Marshaller<String, ByteBuffer> getMarshaller() {
		return IdentifierMarshaller.HEX;
	}
}
