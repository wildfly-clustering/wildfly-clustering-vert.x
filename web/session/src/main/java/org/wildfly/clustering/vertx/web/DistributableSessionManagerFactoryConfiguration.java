/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.Deployment;
import io.vertx.core.json.JsonObject;

import org.wildfly.clustering.function.Supplier;
import org.wildfly.clustering.marshalling.ByteBufferMarshaller;
import org.wildfly.clustering.server.immutable.Immutability;
import org.wildfly.clustering.session.SessionAttributePersistenceStrategy;
import org.wildfly.clustering.session.SessionManagerFactoryConfiguration;

/**
 * Configuration of a distributable session manager factory.
 * @author Paul Ferraro
 */
public class DistributableSessionManagerFactoryConfiguration implements SessionManagerFactoryConfiguration<Void> {
	/** The name of the property used to configure the deployment name */
	public static final String DEPLOYMENT_NAME = "deploymentName";
	/** The name of the property used to configure the session granularity */
	public static final String GRANULARITY = "granularity";
	/** The name of the property used to configure the session attribute marshaller */
	public static final String MARSHALLER = "marshaller";
	/** The name of the property used to configure the maximum active sessions */
	public static final String MAX_ACTIVE_SESSIONS = "maxActiveSessions";
	/** The name of the property used to configure the idle timeout */
	public static final String IDLE_TIMEOUT = "idleTimeout";

	private final String deploymentName;
	private final String serverName;
	private final ClassLoader loader;
	private final OptionalInt maxSize;
	private final Optional<Duration> idleTimeout;
	private final SessionAttributePersistenceStrategy persistenceStrategy;
	private final ByteBufferMarshaller marshaller;
	private final Immutability immutability;

	/**
	 * Creates a new session manager factory configuration.
	 * @param context the vertx context
	 * @param options the options from which configuration will be read
	 */
	public DistributableSessionManagerFactoryConfiguration(Context context, JsonObject options) {
		this((ContextInternal) context, options);
	}

	private DistributableSessionManagerFactoryConfiguration(ContextInternal context, JsonObject options) {
		Optional<Deployment> deployment = Optional.ofNullable(context.getDeployment());
		this.loader = deployment.map(Deployment::deploymentOptions).map(DeploymentOptions::getClassLoader).orElse(context.classLoader());
		this.deploymentName = options.getString(DEPLOYMENT_NAME, DistributableSessionStore.class.getSimpleName());
		this.serverName = deployment.map(Deployment::verticleIdentifier).orElse(VertxOptions.DEFAULT_HA_GROUP);
		this.maxSize = Optional.ofNullable(options.getInteger(MAX_ACTIVE_SESSIONS)).map(OptionalInt::of).orElse(OptionalInt.empty());
		this.idleTimeout = Optional.ofNullable(options.getString(IDLE_TIMEOUT)).map(Duration::parse);
		this.persistenceStrategy = SessionPersistenceGranularity.valueOf(options.getString(GRANULARITY, SessionPersistenceGranularity.ATTRIBUTE.name())).get();
		Function<ClassLoader, ByteBufferMarshaller> marshallerFactory = SessionAttributeMarshaller.valueOf(options.getString(MARSHALLER, SessionAttributeMarshaller.JBOSS.name()));
		this.marshaller = marshallerFactory.apply(this.loader);
		List<Immutability> loadedImmutabilities = new LinkedList<>();
		for (Immutability loadedImmutability : ServiceLoader.load(Immutability.class, this.loader)) {
			loadedImmutabilities.add(loadedImmutability);
		}
		this.immutability = Immutability.composite(Stream.concat(Stream.of(Immutability.getDefault()), loadedImmutabilities.stream()).toList());
	}

	@Override
	public String getDeploymentName() {
		return this.deploymentName;
	}

	@Override
	public String getServerName() {
		return this.serverName;
	}

	@Override
	public ClassLoader getClassLoader() {
		return this.loader;
	}

	@Override
	public OptionalInt getMaxSize() {
		return this.maxSize;
	}

	@Override
	public Optional<Duration> getIdleTimeout() {
		return this.idleTimeout;
	}

	@Override
	public ByteBufferMarshaller getMarshaller() {
		return this.marshaller;
	}

	@Override
	public Supplier<Void> getSessionContextFactory() {
		return Supplier.of(null);
	}

	@Override
	public Immutability getImmutability() {
		return this.immutability;
	}

	@Override
	public SessionAttributePersistenceStrategy getAttributePersistenceStrategy() {
		return this.persistenceStrategy;
	}
}
