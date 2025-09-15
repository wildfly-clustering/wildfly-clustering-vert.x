/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.remote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.wildfly.clustering.cache.ContainerProvider;
import org.wildfly.clustering.cache.infinispan.remote.InfinispanServerContainer;
import org.wildfly.clustering.cache.infinispan.remote.InfinispanServerExtension;
import org.wildfly.clustering.vertx.web.AbstractSessionStoreITCase;
import org.wildfly.clustering.vertx.web.DistributableSessionManagerFactoryConfiguration;
import org.wildfly.clustering.vertx.web.SessionManagementParameters;

/**
 * @author Paul Ferraro
 */
public class HotRodSessionStoreITCase extends AbstractSessionStoreITCase {
	@RegisterExtension
	static final ContainerProvider<InfinispanServerContainer> INFINISPAN = new InfinispanServerExtension();

	private final Manifest manifest = new Manifest();

	public HotRodSessionStoreITCase() {
		super(Optional.of(Duration.ofSeconds(2)));
	}

	@ParameterizedTest
	@ArgumentsSource(HotRodSessionManagementArgumentsProvider.class)
	public void test(SessionManagementParameters parameters) {
		InfinispanServerContainer container = INFINISPAN.getContainer();
		Attributes attributes = this.manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(new Attributes.Name(HotRodSessionStore.HOTROD_URI), String.format("hotrod://%s:%s@%s:%d", container.getUsername(), container.getPassword(), container.getHost(), container.getPort()));
		// Use local cache since our remote cluster has a single member
		// Reduce expiration interval to speed up expiration verification
		attributes.put(new Attributes.Name(HotRodSessionStore.CONFIGURATION), """
{ "local-cache" : { "expiration" : { "interval" : 1000 }, "transaction" : { "mode" : "BATCH", "locking" : "PESSIMISTIC" }}}""");
		attributes.put(new Attributes.Name(DistributableSessionManagerFactoryConfiguration.GRANULARITY), parameters.getSessionPersistenceGranularity().name());
		attributes.put(new Attributes.Name(DistributableSessionManagerFactoryConfiguration.MARSHALLER), parameters.getSessionMarshallerFactory().name());
		this.run();
	}

	@Override
	public JavaArchive createArchive(org.wildfly.clustering.session.container.SessionManagementTesterConfiguration configuration) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			this.manifest.write(output);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return super.createArchive(configuration).setManifest(new ByteArrayAsset(output.toByteArray()));
	}
}
