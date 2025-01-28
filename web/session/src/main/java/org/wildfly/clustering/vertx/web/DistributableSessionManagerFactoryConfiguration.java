/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.vertx.core.Context;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.Deployment;
import io.vertx.core.json.JsonObject;

import org.wildfly.clustering.marshalling.ByteBufferMarshaller;
import org.wildfly.clustering.server.immutable.Immutability;
import org.wildfly.clustering.session.SessionAttributePersistenceStrategy;
import org.wildfly.clustering.session.SessionManagerFactoryConfiguration;
import org.wildfly.common.function.Functions;

/**
 * Configuration of a distributable session manager factory.
 * @author Paul Ferraro
 */
public class DistributableSessionManagerFactoryConfiguration implements SessionManagerFactoryConfiguration<Void> {
	public static final String DEPLOYMENT_NAME = "deploymentName";
	public static final String GRANULARITY = "granularity";
	public static final String MARSHALLER = "marshaller";
	public static final String MAX_ACTIVE_SESSIONS = "maxActiveSessions";

	private final String deploymentName;
	private final String serverName;
	private final ClassLoader loader;
	private final OptionalInt maxActiveSessions;
	private final SessionAttributePersistenceStrategy persistenceStrategy;
	private final ByteBufferMarshaller marshaller;
	private final Immutability immutability;

	public DistributableSessionManagerFactoryConfiguration(Context context, JsonObject options) {
		Deployment deployment = ((ContextInternal) context).getDeployment();
		this.loader = (deployment != null) ? deployment.deploymentOptions().getClassLoader() : this.getClass().getClassLoader();
		this.deploymentName = options.getString(DEPLOYMENT_NAME, DistributableSessionStore.class.getSimpleName());
		this.serverName = (deployment != null) ? deployment.verticleIdentifier() : VertxOptions.DEFAULT_HA_GROUP;
		this.maxActiveSessions = Optional.ofNullable(options.getInteger(MAX_ACTIVE_SESSIONS)).map(OptionalInt::of).orElse(OptionalInt.empty());
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
	public OptionalInt getMaxActiveSessions() {
		return this.maxActiveSessions;
	}

	@Override
	public ByteBufferMarshaller getMarshaller() {
		return this.marshaller;
	}

	@Override
	public Supplier<Void> getSessionContextFactory() {
		return Functions.constantSupplier(null);
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
