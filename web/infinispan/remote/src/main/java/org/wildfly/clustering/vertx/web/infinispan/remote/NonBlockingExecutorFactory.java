/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.remote;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.commons.executors.ExecutorFactory;
import org.infinispan.commons.executors.NonBlockingResource;
import org.wildfly.clustering.context.DefaultThreadFactory;

/**
 * Factory creating non-blocking executors.
 * @author Paul Ferraro
 */
public class NonBlockingExecutorFactory implements ExecutorFactory {

	static class NonBlockingThreadGroup extends ThreadGroup implements NonBlockingResource {
		NonBlockingThreadGroup(String name) {
			super(name);
		}
	}

	private final ClassLoader loader;

	public NonBlockingExecutorFactory(ClassLoader loader) {
		this.loader = loader;
	}

	@Override
	public ExecutorService getExecutor(Properties props) {
		ConfigurationProperties properties = new ConfigurationProperties(props);
		String threadNamePrefix = properties.getDefaultExecutorFactoryThreadNamePrefix();
		String threadNameSuffix = properties.getDefaultExecutorFactoryThreadNameSuffix();
		NonBlockingThreadGroup group = new NonBlockingThreadGroup(threadNamePrefix + "-group");
		ThreadFactory factory = new ThreadFactory() {
			private final AtomicInteger counter = new AtomicInteger(0);

			@Override
			public Thread newThread(Runnable task) {
				int threadIndex = this.counter.incrementAndGet();
				Thread thread = new Thread(group, task, threadNamePrefix + "-" + threadIndex + threadNameSuffix);
				thread.setDaemon(true);
				return thread;
			}
		};

		return new ThreadPoolExecutor(properties.getDefaultExecutorFactoryPoolSize(), properties.getDefaultExecutorFactoryPoolSize(), 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), new DefaultThreadFactory(factory, this.loader));
	}
}
