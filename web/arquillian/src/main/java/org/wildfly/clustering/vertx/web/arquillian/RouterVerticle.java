/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.arquillian;

import java.util.concurrent.Callable;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.SessionStore;

import org.jboss.arquillian.container.test.spi.util.ServiceLoader;

/**
 * A verticle that adds routes to a router.
 * @author Paul Ferraro
 */
public class RouterVerticle extends AbstractVerticle {
	private final Router router;
	private volatile Context context;
	private volatile Future<SessionStore> store;

	public RouterVerticle(Router router) {
		this.router = router;
	}

	@Override
	public void init(Vertx vertx, Context context) {
		this.context = context;
	}

	@Override
	public void start(Promise<Void> promise) {
		// DistributableSessionStore.init(...) is blocking
		this.store = this.context.executeBlocking(this::loadSessionStore);
		Future<Void> future = this.store.map(store -> {
			this.router.route().setName(SessionHandler.class.getName()).handler(SessionHandler.create(store));

			// Load router configurators from context class loader
			for (RouterConfigurator configurator : ServiceLoader.load(RouterConfigurator.class, ((ContextInternal) this.context).classLoader())) {
				configurator.accept(this.router);
			}
			return null;
		});
		future.onComplete(promise::succeed, promise::fail);
	}

	@Override
	public void stop(Promise<Void> promise) {
		SessionStore store = this.store.result();
		if (store != null) {
			Callable<Void> task = () -> {
				this.router.clear();
				store.close();
				return null;
			};
			this.context.executeBlocking(task).onComplete(promise::succeed, promise::fail);
		} else {
			promise.fail(new IllegalStateException());
		}
	}

	SessionStore loadSessionStore() {
		return SessionStore.create(this.context.owner(), this.context.config());
	}
}
