/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.remote;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.wildfly.clustering.cache.ContainerProvider;
import org.wildfly.clustering.cache.infinispan.remote.InfinispanServerContainer;
import org.wildfly.clustering.cache.infinispan.remote.InfinispanServerExtension;
import org.wildfly.clustering.vertx.web.AbstractSessionStoreITCase;
import org.wildfly.clustering.vertx.web.SessionAttributeMarshaller;
import org.wildfly.clustering.vertx.web.SessionManagementArguments;
import org.wildfly.clustering.vertx.web.SessionPersistenceGranularity;

/**
 * @author Paul Ferraro
 */
public class HotRodSessionStoreITCase extends AbstractSessionStoreITCase<SessionManagementArguments> {
	@RegisterExtension
	static final ContainerProvider<InfinispanServerContainer> INFINISPAN = new InfinispanServerExtension();

	@ParameterizedTest
	@ArgumentsSource(HotRodSessionManagementArgumentsProvider.class)
	public void test(SessionManagementArguments arguments) {
		InfinispanServerContainer container = INFINISPAN.getContainer();
		this.accept(new SessionManagementArguments() {
			@Override
			public SessionPersistenceGranularity getSessionPersistenceGranularity() {
				return arguments.getSessionPersistenceGranularity();
			}

			@Override
			public SessionAttributeMarshaller getSessionMarshallerFactory() {
				return arguments.getSessionMarshallerFactory();
			}

			@Override
			public Manifest getManifest() {
				Manifest manifest = SessionManagementArguments.super.getManifest();
				Attributes attributes = manifest.getMainAttributes();
				attributes.put(new Attributes.Name(HotRodSessionStore.HOTROD_URI), container.get().toString(true));
				// Use local cache since our remote cluster has a single member
				// Reduce expiration interval to speed up expiration verification
				attributes.put(new Attributes.Name(HotRodSessionStore.CONFIGURATION), """
		{ "local-cache" : { "encoding" : { "key" : { "media-type" : "application/octet-stream" }, "value" : { "media-type" : "application/octet-stream" }}, "expiration" : { "interval" : 1000 }, "locking" : { "isolation" : "REPEATABLE_READ" }, "transaction" : { "mode" : "NON_XA", "locking" : "PESSIMISTIC" }}}""");
				return manifest;
			}
		});
	}
}
