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
 * @author Paul Ferraro
 */
public class VertxWebContainerConfiguration implements ContainerConfiguration {

	private volatile VertxOptions options = new VertxOptions();
	private volatile HttpServerOptions httpServerOptions = new HttpServerOptions();

	@Override
	public void validate(){
	}

	public VertxOptions getOptions() {
		return this.options;
	}

	public HttpServerOptions getHttpServerOptions() {
		return this.httpServerOptions;
	}

	public void setOptions(String options) {
		try {
			this.options = new VertxOptions(new JsonObject(options));
		} catch (DecodeException e) {
			throw new ConfigurationException(e);
		}
	}

	public void setHttpServerOptions(String options) {
		try {
			this.httpServerOptions = new HttpServerOptions(new JsonObject(options));
		} catch (DecodeException e) {
			throw new ConfigurationException(e);
		}
	}
}
