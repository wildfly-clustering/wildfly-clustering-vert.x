/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.vertx.web.infinispan.remote;

import java.util.EnumSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.wildfly.clustering.vertx.web.SessionAttributeMarshaller;
import org.wildfly.clustering.vertx.web.SessionManagementArguments;
import org.wildfly.clustering.vertx.web.SessionPersistenceGranularity;

/**
 * @author Paul Ferraro
 */
public class HotRodSessionManagementArgumentsProvider implements ArgumentsProvider {

	@Override
	public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
		Stream.Builder<Arguments> builder = Stream.builder();
		for (SessionPersistenceGranularity granularity : EnumSet.allOf(SessionPersistenceGranularity.class)) {
			for (SessionAttributeMarshaller marshaller : EnumSet.allOf(SessionAttributeMarshaller.class)) {
				builder.add(Arguments.of(new SessionManagementArguments() {
					@Override
					public SessionPersistenceGranularity getSessionPersistenceGranularity() {
						return granularity;
					}

					@Override
					public SessionAttributeMarshaller getSessionMarshallerFactory() {
						return marshaller;
					}

					@Override
					public String toString() {
						return String.format("%s-%s", granularity, marshaller).toString();
					}
				}));
			}
		}
		return builder.build();
	}
}
