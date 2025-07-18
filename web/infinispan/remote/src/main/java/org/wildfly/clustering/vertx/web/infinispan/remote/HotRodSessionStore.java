/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.remote;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.sstore.SessionStore;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.client.hotrod.configuration.TransactionMode;
import org.infinispan.client.hotrod.impl.HotRodURI;
import org.infinispan.commons.executors.NonBlockingResource;
import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.cache.infinispan.marshalling.MediaTypes;
import org.wildfly.clustering.cache.infinispan.marshalling.UserMarshaller;
import org.wildfly.clustering.cache.infinispan.remote.RemoteCacheConfiguration;
import org.wildfly.clustering.marshalling.protostream.ClassLoaderMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamByteBufferMarshaller;
import org.wildfly.clustering.marshalling.protostream.SerializationContextBuilder;
import org.wildfly.clustering.session.SessionManagerFactory;
import org.wildfly.clustering.session.SessionManagerFactoryConfiguration;
import org.wildfly.clustering.session.infinispan.remote.HotRodSessionManagerFactory;
import org.wildfly.clustering.vertx.web.DistributableSessionManagerFactoryConfiguration;
import org.wildfly.clustering.vertx.web.DistributableSessionStore;
import org.wildfly.clustering.vertx.web.VertxSessionSpecificationProvider;

/**
 * A remote Infinispan {@link SessionStore} for Vert.x.
 * @author Paul Ferraro
 */
@MetaInfServices(SessionStore.class)
public class HotRodSessionStore extends DistributableSessionStore {
	public static final String HOTROD_URI = "uri";
	public static final String CONFIGURATION = "configuration";
	public static final String PROPERTIES = "properties";
	private static final String DEFAULT_CONFIGURATION = """
{
	"distributed-cache" : {
		"mode" : "SYNC",
		"statistics" : "true",
	}
}""";

	static class NonBlockingThreadGroup extends ThreadGroup implements NonBlockingResource {
		NonBlockingThreadGroup(String name) {
			super(name);
		}
	}
	private static final AtomicInteger COUNTER = new AtomicInteger(0);

	public HotRodSessionStore() {
		this(new LinkedList<>());
	}

	private HotRodSessionStore(List<Runnable> closeTasks) {
		super(new BiFunction<>() {
			@Override
			public SessionManagerFactory<Context, Void> apply(Context context, JsonObject options) {
				SessionManagerFactoryConfiguration<Void> factoryConfiguration = new DistributableSessionManagerFactoryConfiguration(context, options);
				ClassLoader loader = factoryConfiguration.getClassLoader();

				URI uri = URI.create(Objects.requireNonNull(options.getString(HOTROD_URI)));
				String cacheConfiguration = options.getString(CONFIGURATION, DEFAULT_CONFIGURATION);
				Properties properties = new Properties();
				properties.putAll(options.getJsonObject(PROPERTIES, JsonObject.of()).getMap());

				COUNTER.incrementAndGet();
				closeTasks.add(0, () -> {
					// Stop RxJava schedulers when no longer in use
					if (COUNTER.decrementAndGet() == 0) {
						Schedulers.shutdown();
					}
				});

				Configuration configuration = ((uri != null) ? HotRodURI.create(uri).toConfigurationBuilder() : new ConfigurationBuilder())
						.withProperties(properties)
						.marshaller(new UserMarshaller(MediaTypes.WILDFLY_PROTOSTREAM, new ProtoStreamByteBufferMarshaller(SerializationContextBuilder.newInstance(ClassLoaderMarshaller.of(loader)).load(loader).build())))
						.asyncExecutorFactory().factory(new NonBlockingExecutorFactory(loader))
						.build();

				RemoteCacheManager container = new RemoteCacheManager(configuration, false);
				container.start();
				closeTasks.add(0, container::close);

				String deploymentName = factoryConfiguration.getDeploymentName();
				OptionalInt maxActiveSessions = factoryConfiguration.getMaxActiveSessions();

				container.getConfiguration().addRemoteCache(deploymentName, builder -> builder.forceReturnValues(false).nearCacheMode(maxActiveSessions.isEmpty() ? NearCacheMode.DISABLED : NearCacheMode.INVALIDATED).transactionMode(TransactionMode.NONE).configuration(cacheConfiguration));

				RemoteCache<?, ?> cache = container.getCache(deploymentName);

				cache.start();
				closeTasks.add(0, cache::stop);

				return new HotRodSessionManagerFactory<>(factoryConfiguration, VertxSessionSpecificationProvider.INSTANCE, VertxSessionSpecificationProvider.INSTANCE, new RemoteCacheConfiguration() {
					@SuppressWarnings("unchecked")
					@Override
					public <CK, CV> RemoteCache<CK, CV> getCache() {
						return (RemoteCache<CK, CV>) cache;
					}
				});
			}
		}, () -> closeTasks.forEach(Runnable::run));
	}
}
