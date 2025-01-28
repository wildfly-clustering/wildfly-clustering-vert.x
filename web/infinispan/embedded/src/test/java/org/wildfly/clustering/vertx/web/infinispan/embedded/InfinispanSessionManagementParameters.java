/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.vertx.web.infinispan.embedded;

import org.wildfly.clustering.vertx.web.SessionManagementParameters;

/**
 * @author Paul Ferraro
 */
public interface InfinispanSessionManagementParameters extends SessionManagementParameters {

	String getTemplate();
}
