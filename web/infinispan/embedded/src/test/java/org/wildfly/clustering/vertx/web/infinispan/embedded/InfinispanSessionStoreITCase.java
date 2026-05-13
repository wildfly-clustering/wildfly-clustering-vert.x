/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.embedded;

import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.wildfly.clustering.vertx.web.AbstractSessionStoreITCase;

/**
 * @author Paul Ferraro
 */
public class InfinispanSessionStoreITCase extends AbstractSessionStoreITCase<InfinispanSessionManagementArguments> {

	@ParameterizedTest
	@ArgumentsSource(InfinispanSessionManagementArgumentsProvider.class)
	public void test(InfinispanSessionManagementArguments arguments) {
		this.accept(arguments);
	}

	@Override
	public JavaArchive createArchive(InfinispanSessionManagementArguments arguments) {
		return super.createArchive(arguments).addAsResource("infinispan.xml");
	}
}
