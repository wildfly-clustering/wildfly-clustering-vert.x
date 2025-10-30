/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.arquillian;

import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

/**
 * Encapsulates the configuration of a Vert.x Web container.
 * @author Paul Ferraro
 */
public class VertxWebContainerConfiguration implements ContainerConfiguration {

	private volatile VertxOptions options = new VertxOptions();
	private volatile HttpServerOptions httpServerOptions = new HttpServerOptions();

	/**
	 * Creates a new container configuration.
	 */
	public VertxWebContainerConfiguration() {
	}

	@Override
	public void validate(){
	}

	/**
	 * Returns the Vert.x options.
	 * @return the Vert.x options.
	 */
	public VertxOptions getOptions() {
		return this.options;
	}

	/**
	 * Returns the server options.
	 * @return the server options.
	 */
	public HttpServerOptions getHttpServerOptions() {
		return this.httpServerOptions;
	}

	/**
	 * Applies the specified Vert.x options.
	 * @param options a json string
	 */
	public void setOptions(String options) {
		try {
			this.options = new VertxOptions(new JsonObject(options));
		} catch (DecodeException e) {
			throw new ConfigurationException(e);
		}
	}

	/**
	 * Applies the specified server options.
	 * @param options a json string
	 */
	public void setHttpServerOptions(String options) {
		try {
			this.httpServerOptions = new HttpServerOptions(new JsonObject(options));
		} catch (DecodeException e) {
			throw new ConfigurationException(e);
		}
	}
}
