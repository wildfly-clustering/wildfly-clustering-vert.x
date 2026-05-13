/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.vertx.web;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Paul Ferraro
 */
public interface SessionManagementArguments {

	SessionPersistenceGranularity getSessionPersistenceGranularity();

	SessionAttributeMarshaller getSessionMarshallerFactory();

	default Manifest getManifest() {
		Manifest manifest = new Manifest();
		Attributes attributes = manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(new Attributes.Name(DistributableSessionManagerFactoryConfiguration.GRANULARITY), this.getSessionPersistenceGranularity().name());
		attributes.put(new Attributes.Name(DistributableSessionManagerFactoryConfiguration.MARSHALLER), this.getSessionMarshallerFactory().name());
		return manifest;
	}
}
