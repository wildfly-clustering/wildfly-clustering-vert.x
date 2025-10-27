/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.arquillian;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.sstore.SessionStore;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

/**
 * An embedded Vert.x container.
 * @author Paul Ferraro
 */
public class VertxWebContainer implements DeployableContainer<VertxWebContainerConfiguration> {
	private volatile Vertx vertx;
	private volatile VertxOptions options;
	private volatile HttpServer server;
	private volatile HttpServerOptions serverOptions;
	private volatile Router router;
	private final Map<String, Map.Entry<String, Route>> deployments = new ConcurrentHashMap<>();

	/**
	 * Creates a new Vert.x Web container.
	 */
	public VertxWebContainer() {
	}

	@Override
	public Class<VertxWebContainerConfiguration> getConfigurationClass() {
		return VertxWebContainerConfiguration.class;
	}

	@Override
	public void setup(VertxWebContainerConfiguration configuration) {
		this.options = new VertxOptions(configuration.getOptions());
		this.serverOptions = new HttpServerOptions(configuration.getHttpServerOptions());
	}

	@Override
	public void start() throws LifecycleException {
		this.vertx = Vertx.builder().with(this.options).build();
		this.router = Router.router(this.vertx);
		this.server = this.vertx.createHttpServer(this.serverOptions).requestHandler(this.router);
		try {
			await(this.server.listen());
		} catch (CompletionException e) {
			throw new LifecycleException(e.getLocalizedMessage(), e);
		}
	}

	@Override
	public void stop() throws LifecycleException {
		try {
			await(this.server.close());
			this.router.clear();
			await(this.vertx.close());
		} catch (CompletionException e) {
			throw new LifecycleException(e.getLocalizedMessage(), e);
		}
	}

	@Override
	public ProtocolDescription getDefaultProtocol() {
		return new ProtocolDescription("Vert.x");
	}

	@Override
	public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
		String contextName = getContextName(archive);
		String contextPath = contextName.equals("ROOT") ? "/" + contextName : "";
		String contextRoutePath = contextPath + "/*";
		Router parentRouter = this.router;
		Router contextRouter = Router.router(this.vertx);
		try {
			File file = Files.createTempDirectory("vertx").resolve(contextName).toFile();
			archive.as(ZipExporter.class).exportTo(file, true);
			file.deleteOnExit();
			ClassLoader loader = new URLClassLoader(new URL[] { file.toURI().toURL() }, SessionStore.class.getClassLoader());
			JsonObject options = new JsonObject();
			try (JarFile jarFile = new JarFile(file)) {
				for (Map.Entry<Object, Object> entry : jarFile.getManifest().getMainAttributes().entrySet()) {
					options.put(entry.getKey().toString(), entry.getValue());
				}
			}
			options.put("deploymentName", contextName);
			String deploymentId = await(this.vertx.deployVerticle(new RouterVerticle(contextRouter), new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setClassLoader(loader).setConfig(options)));
			Route contextRoute = parentRouter.route(contextRoutePath).subRouter(contextRouter);
			this.deployments.put(contextName, Map.entry(deploymentId, contextRoute));
		} catch (CompletionException | IOException e) {
			throw new DeploymentException(e.getLocalizedMessage(), e);
		}

		HTTPContext context = new HTTPContext(this.serverOptions.getHost(), this.server.actualPort());
		// Expose routes as arquillian servlets
		for (Route route : contextRouter.getRoutes()) {
			context.add(new Servlet(route.getName(), contextPath));
		}
		return new ProtocolMetaData().addContext(context);
	}

	@Override
	public void undeploy(Archive<?> archive) throws DeploymentException {
		String contextName = getContextName(archive);
		Map.Entry<String, Route> deployment = this.deployments.remove(contextName);
		if (deployment != null) {
			deployment.getValue().remove();
			try {
				await(this.vertx.undeploy(deployment.getKey()));
			} catch (CompletionException e) {
				throw new DeploymentException(e.getLocalizedMessage(), e);
			}
		}
	}

	static String getContextName(Archive<?> archive) {
		String archiveName = archive.getName();
		// Strip filename extension
		int extensionSeparatorIndex = archiveName.lastIndexOf('.');
		return (extensionSeparatorIndex >= 0) ? archiveName.substring(0, extensionSeparatorIndex) : archiveName;
	}

	static <T> T await(Future<T> future) {
		CompletableFuture<T> result = new CompletableFuture<>();
		future.onComplete(result::complete, result::completeExceptionally);
		// Block until started
		return result.join();
	}
}
