/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.embedded;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.management.ObjectName;

import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.sstore.SessionStore;

import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.GlobalJmxConfiguration;
import org.infinispan.configuration.global.ShutdownHookBehavior;
import org.infinispan.configuration.global.TransportConfiguration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.expiration.ExpirationManager;
import org.infinispan.globalstate.ConfigurationStorage;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.impl.ListenerInvocation;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.remoting.transport.jgroups.JGroupsChannelConfigurator;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.infinispan.transaction.tm.EmbeddedTransactionManager;
import org.infinispan.util.concurrent.BlockingManager;
import org.infinispan.util.concurrent.NonBlockingManager;
import org.jboss.logging.Logger;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.jmx.JmxConfigurator;
import org.jgroups.util.Util;
import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.cache.infinispan.embedded.EmbeddedCacheConfiguration;
import org.wildfly.clustering.cache.infinispan.embedded.container.DataContainerConfigurationBuilder;
import org.wildfly.clustering.cache.infinispan.marshalling.MediaTypes;
import org.wildfly.clustering.cache.infinispan.marshalling.UserMarshaller;
import org.wildfly.clustering.marshalling.ByteBufferMarshaller;
import org.wildfly.clustering.marshalling.protostream.ClassLoaderMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamByteBufferMarshaller;
import org.wildfly.clustering.marshalling.protostream.SerializationContextBuilder;
import org.wildfly.clustering.server.group.GroupCommandDispatcherFactory;
import org.wildfly.clustering.server.infinispan.dispatcher.CacheContainerCommandDispatcherFactory;
import org.wildfly.clustering.server.infinispan.dispatcher.ChannelEmbeddedCacheManagerCommandDispatcherFactoryConfiguration;
import org.wildfly.clustering.server.infinispan.dispatcher.EmbeddedCacheManagerCommandDispatcherFactory;
import org.wildfly.clustering.server.infinispan.dispatcher.LocalEmbeddedCacheManagerCommandDispatcherFactoryConfiguration;
import org.wildfly.clustering.server.jgroups.ChannelGroupMember;
import org.wildfly.clustering.server.jgroups.dispatcher.ChannelCommandDispatcherFactory;
import org.wildfly.clustering.server.jgroups.dispatcher.JChannelCommandDispatcherFactory;
import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.SessionManagerFactory;
import org.wildfly.clustering.session.SessionManagerFactoryConfiguration;
import org.wildfly.clustering.session.infinispan.embedded.InfinispanSessionManagerFactory;
import org.wildfly.clustering.session.infinispan.embedded.metadata.SessionMetaDataKey;
import org.wildfly.clustering.session.spec.SessionEventListenerSpecificationProvider;
import org.wildfly.clustering.session.spec.SessionSpecificationProvider;
import org.wildfly.clustering.vertx.web.DistributableSessionManagerFactoryConfiguration;
import org.wildfly.clustering.vertx.web.DistributableSessionStore;
import org.wildfly.clustering.vertx.web.VertxSessionSpecificationProvider;

/**
 * An embedded Infinispan {@link SessionStore} for Vert.x.
 */
@MetaInfServices(SessionStore.class)
public class InfinispanSessionStore extends DistributableSessionStore {
	/** The name of the property specifying the location of the Infinispan configuration resource. */
	public static final String RESOURCE = "resource";
	/** The name of the property specifying the name of a cache configuration. */
	public static final String CACHE = "cache";

	static final Logger LOGGER = Logger.getLogger(InfinispanSessionStore.class);
	static final String DEFAULT_RESOURCE = "infinispan.xml";
	private static final AtomicInteger COUNTER = new AtomicInteger(0);

	/**
	 * Creates a session store.
	 */
	public InfinispanSessionStore() {
		this(new LinkedList<>());
	}

	private InfinispanSessionStore(List<Runnable> closeTasks) {
		super(new BiFunction<>() {
			@Override
			public SessionManagerFactory<Context, Void> apply(Context context, JsonObject options) {
				SessionManagerFactoryConfiguration<Void> configuration = new DistributableSessionManagerFactoryConfiguration(context, options);
				ClassLoader loader = configuration.getClassLoader();
				String deploymentName = configuration.getDeploymentName();
				String resourceName = options.getString(RESOURCE, DEFAULT_RESOURCE);
				String templateName = options.getString(CACHE);

				COUNTER.incrementAndGet();
				closeTasks.add(0, () -> {
					// Stop RxJava schedulers when no longer in use
					if (COUNTER.decrementAndGet() == 0) {
						Schedulers.shutdown();
					}
				});

				try {
					// Locate as classpath resource
					URL url = loader.getResource(resourceName);
					if (url == null) {
						// Attempt to locate on filesystem
						File file = new File(resourceName);
						if (file.exists()) {
							url = file.toURI().toURL();
						} else {
							throw new IllegalArgumentException(resourceName);
						}
					}
					ConfigurationBuilderHolder holder = new ParserRegistry(loader, false, System.getProperties()).parse(url);
					GlobalConfigurationBuilder global = holder.getGlobalConfigurationBuilder();
					String containerName = global.cacheContainer().name();
					TransportConfiguration transport = Optional.of(global.transport().create()).filter(t -> t.nodeName() != null).orElseGet(() -> global.transport().nodeName(Util.generateLocalName()).create());

					JGroupsChannelConfigurator configurator = (transport.transport() != null) ? new JChannelConfigurator(transport, loader) : null;
					JChannel channel = (configurator != null) ? configurator.createChannel(null) : null;
					if (channel != null) {
						channel.setName(transport.nodeName());
						channel.setDiscardOwnMessages(true);
						LOGGER.debugf("Connecting %s to %s", transport.nodeName(), transport.clusterName());
						channel.connect(transport.clusterName());
						LOGGER.debugf("Connected %s to %s with view: %s", channel.getName(), channel.getClusterName(), channel.view().getMembers());
						closeTasks.add(0, () -> {
							LOGGER.debugf("Disconnecting %s from %s with view: %s", channel.getName(), channel.getClusterName(), channel.view().getMembers());
							try {
								channel.disconnect();
								LOGGER.debugf("Disconnected %s from %s", transport.nodeName(), transport.clusterName());
							} finally {
								channel.close();
							}
						});

						GlobalJmxConfiguration jmx = global.jmx().create();
						if (jmx.enabled()) {
							ObjectName prefix = new ObjectName(jmx.domain(), "manager", ObjectName.quote(containerName));
							JmxConfigurator.registerChannel(channel, ManagementFactory.getPlatformMBeanServer(), prefix, transport.clusterName(), true);
							closeTasks.add(0, () -> {
								try {
									JmxConfigurator.unregisterChannel(channel, ManagementFactory.getPlatformMBeanServer(), prefix, transport.clusterName());
								} catch (Exception e) {
									LOGGER.warn(e.getLocalizedMessage(), e);
								}
							});
						}

						Properties properties = new Properties();
						properties.put(JGroupsTransport.CHANNEL_CONFIGURATOR, new ForkChannelConfigurator(channel, containerName));
						global.transport().withProperties(properties);
					}

					ChannelCommandDispatcherFactory channelCommandDispatcherFactory = (channel != null) ? new JChannelCommandDispatcherFactory(new JChannelCommandDispatcherFactory.Configuration() {
						@Override
						public JChannel getChannel() {
							return channel;
						}

						@Override
						public ByteBufferMarshaller getMarshaller() {
							return this.getMarshallerFactory().apply(JChannelCommandDispatcherFactory.class.getClassLoader());
						}

						@Override
						public Function<ClassLoader, ByteBufferMarshaller> getMarshallerFactory() {
							return loader -> new ProtoStreamByteBufferMarshaller(SerializationContextBuilder.newInstance(ClassLoaderMarshaller.of(loader)).load(loader).build());
						}

						@Override
						public Predicate<Message> getUnknownForkPredicate() {
							return Predicate.not(Message::hasPayload);
						}
					}) : null;
					if (channelCommandDispatcherFactory != null) {
						closeTasks.add(0, channelCommandDispatcherFactory::close);
					}

					global.classLoader(loader)
							.shutdown().hookBehavior(ShutdownHookBehavior.DONT_REGISTER)
							.blockingThreadPool().threadFactory(new DefaultBlockingThreadFactory(BlockingManager.class))
							.expirationThreadPool().threadFactory(new DefaultBlockingThreadFactory(ExpirationManager.class))
							.listenerThreadPool().threadFactory(new DefaultBlockingThreadFactory(ListenerInvocation.class))
							.nonBlockingThreadPool().threadFactory(new DefaultNonBlockingThreadFactory(NonBlockingManager.class))
							.serialization()
								.marshaller(new UserMarshaller(MediaTypes.WILDFLY_PROTOSTREAM, new ProtoStreamByteBufferMarshaller(SerializationContextBuilder.newInstance(ClassLoaderMarshaller.of(loader)).load(loader).build())))
								// Register dummy serialization context initializer, to bypass service loading in org.infinispan.marshall.protostream.impl.SerializationContextRegistryImpl
								// Otherwise marshaller auto-detection will not work
								.addContextInitializer(new SerializationContextInitializer() {
									@Deprecated
									@Override
									public String getProtoFile() {
										return null;
									}

									@Deprecated
									@Override
									public String getProtoFileName() {
										return null;
									}

									@Override
									public void registerMarshallers(SerializationContext context) {
									}

									@Override
									public void registerSchema(SerializationContext context) {
									}
								})
							.globalState().configurationStorage(ConfigurationStorage.IMMUTABLE).disable();

					EmbeddedCacheManager container = new DefaultCacheManager(holder, false);
					container.start();
					closeTasks.add(0, container::stop);

					Configuration template = (templateName != null) ? container.getCacheConfiguration(templateName) : container.getDefaultCacheConfiguration();
					if (template == null) {
						throw new IllegalArgumentException(templateName);
					}
					ConfigurationBuilder builder = new ConfigurationBuilder().read(template).template(false);
					builder.encoding().mediaType(MediaType.APPLICATION_OBJECT_TYPE);

					if (template.invocationBatching().enabled()) {
						builder.transaction().transactionManagerLookup(EmbeddedTransactionManager::getInstance);
					}

					// Disable expiration
					builder.expiration().lifespan(-1).maxIdle(-1).disableReaper().wakeUpInterval(-1);

					OptionalInt maxActiveSessions = configuration.getMaxSize();
					Optional<Duration> idleTimeout = configuration.getIdleTimeout();
					EvictionStrategy eviction = maxActiveSessions.isPresent() ? EvictionStrategy.REMOVE : EvictionStrategy.MANUAL;
					builder.memory().storage(StorageType.HEAP)
							.whenFull(eviction)
							.maxCount(maxActiveSessions.orElse(-1))
							;
					if (eviction.isEnabled()) {
						// Only evict meta-data entries
						// We will cascade eviction to the remaining entries for a given session
						DataContainerConfigurationBuilder containerBuilder = builder.addModule(DataContainerConfigurationBuilder.class);
						containerBuilder.evictable(SessionMetaDataKey.class::isInstance);
						idleTimeout.ifPresent(containerBuilder::idleTimeout);
					}

					container.defineConfiguration(deploymentName, builder.build());
					closeTasks.add(0, () -> container.undefineConfiguration(deploymentName));

					CacheContainerCommandDispatcherFactory commandDispatcherFactory = (channelCommandDispatcherFactory != null) ? new EmbeddedCacheManagerCommandDispatcherFactory<>(new ChannelEmbeddedCacheManagerCommandDispatcherFactoryConfiguration() {
						@Override
						public GroupCommandDispatcherFactory<org.jgroups.Address, ChannelGroupMember> getCommandDispatcherFactory() {
							return channelCommandDispatcherFactory;
						}

						@Override
						public EmbeddedCacheManager getCacheContainer() {
							return container;
						}
					}) : new EmbeddedCacheManagerCommandDispatcherFactory<>(new LocalEmbeddedCacheManagerCommandDispatcherFactoryConfiguration() {
						@Override
						public EmbeddedCacheManager getCacheContainer() {
							return container;
						}
					});

					Cache<?, ?> cache = container.getCache(deploymentName);
					cache.start();
					closeTasks.add(0, cache::stop);

					return new InfinispanSessionManagerFactory<>(new InfinispanSessionManagerFactory.Configuration<ImmutableSession, Context, Void, Void>() {
						@Override
						public SessionManagerFactoryConfiguration<Void> getSessionManagerFactoryConfiguration() {
							return configuration;
						}

						@Override
						public SessionSpecificationProvider<ImmutableSession, Context> getSessionSpecificationProvider() {
							return VertxSessionSpecificationProvider.INSTANCE;
						}

						@Override
						public SessionEventListenerSpecificationProvider<ImmutableSession, Void> getSessionEventListenerSpecificationProvider() {
							return VertxSessionSpecificationProvider.INSTANCE;
						}

						@Override
						public CacheContainerCommandDispatcherFactory getCommandDispatcherFactory() {
							return commandDispatcherFactory;
						}

						@Override
						public EmbeddedCacheConfiguration getCacheConfiguration() {
							return new EmbeddedCacheConfiguration() {
								@SuppressWarnings("unchecked")
								@Override
								public <K, V> Cache<K, V> getCache() {
									return (Cache<K, V>) cache;
								}

								@Override
								public boolean isFaultTolerant() {
									return true;
								}
							};
						}
					});
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
		}, () -> closeTasks.forEach(Runnable::run));
	}
}
