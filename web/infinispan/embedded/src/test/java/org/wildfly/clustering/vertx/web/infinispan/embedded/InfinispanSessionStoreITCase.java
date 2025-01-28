/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.embedded;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.wildfly.clustering.vertx.web.AbstractSessionStoreITCase;
import org.wildfly.clustering.vertx.web.DistributableSessionManagerFactoryConfiguration;

/**
 * @author Paul Ferraro
 */
public class InfinispanSessionStoreITCase extends AbstractSessionStoreITCase {

	private final Manifest manifest = new Manifest();

	@ParameterizedTest(name = ParameterizedTest.ARGUMENTS_PLACEHOLDER)
	@ArgumentsSource(InfinispanSessionManagementArgumentsProvider.class)
	public void test(InfinispanSessionManagementParameters parameters) {
		Attributes attributes = this.manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(new Attributes.Name(DistributableSessionManagerFactoryConfiguration.GRANULARITY), parameters.getSessionPersistenceGranularity().name());
		attributes.put(new Attributes.Name(DistributableSessionManagerFactoryConfiguration.MARSHALLER), parameters.getSessionMarshallerFactory().name());
		attributes.put(new Attributes.Name(InfinispanSessionStore.CACHE), parameters.getTemplate());
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
		return super.createArchive(configuration).addAsResource("infinispan.xml").setManifest(new ByteArrayAsset(output.toByteArray()));
	}
}
