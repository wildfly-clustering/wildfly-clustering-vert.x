/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.embedded;

import org.wildfly.clustering.context.DefaultThreadFactory;

/**
 * Thread factory decorator that creates blocking threads.
 * @author Paul Ferraro
 */
public class DefaultBlockingThreadFactory extends DefaultThreadFactory {
	/**
	 * Creates a blocking thread factory using the class loader of the specified class.
	 * @param contextClass the class whose loader should be associated with new threads.
	 */
	public DefaultBlockingThreadFactory(Class<?> contextClass) {
		super(contextClass, contextClass.getClassLoader());
	}
}
