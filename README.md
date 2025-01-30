[![Version](https://img.shields.io/maven-central/v/org.wildfly.clustering.vertx/wildfly-clustering-vertx?style=for-the-badge&logo=redhat&logoColor=ee0000&label=javadoc)](https://javadoc.io/doc/org.wildfly.clustering.vertx/wildfly-clustering-vertx)
[![License](https://img.shields.io/github/license/wildfly-clustering/wildfly-clustering-vert.x?style=for-the-badge&color=darkgreen&logo=apache&logoColor=d22128)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/zulip-chat-lightblue.svg?style=for-the-badge&logo=zulip&logoColor=ffffff)](https://wildfly.zulipchat.com/#narrow/stream/wildfly-clustering)

# WildFly Clustering for Vert.x

wildfly-clustering-vert.x provides a distributed SessionStore for Vert.x Web using the distributed session management functionality of WildFly.
This brings the same distributed session management functionality from WildFly to the Vert.x ecosystem, including:

* Session attribute replication via an embedded cache or persistence to a remote Infinispan cluster.
* Configurable session replication/persistence strategies, i.e. per session vs per attribute.
* Similar semantics to that of a local SessionStore, including a high level of consistency under concurrent request access, and support for mutable session attributes.
* Ability to limit the number of active sessions to retain in local memory
* Configurable session attribute marshallers.

## Building

1.	Clone this repository.

		$ git clone git@github.com:wildfly-clustering/wildfly-clustering-vert.x.git
		$ cd wildfly-clustering-vert.x

1.	Build using Java 17 or higher and Apache Maven 3.8.x or higher.

		$ mvn clean install

## Installation

To bundle a SessionStore implementation wildfly-clustering-vert.x with your application, simply include the appropriate module as a runtime dependency of your application.

e.g.

		<dependency>
			<groupId>org.wildfly.clustering.vertx</group>
			<artifactId>wildfly-clustering-vertx-infinispan-embedded</artifactId>
			<scope>runtime</scope>
		</dependency>

or:

		<dependency>
			<groupId>org.wildfly.clustering.vertx</group>
			<artifactId>wildfly-clustering-vertx-infinispan-remote</artifactId>
			<scope>runtime</scope>
		</dependency>

Provided your classpath has no competing implementations, Vert.x will automatically load and initialise the appriate SessionStore implementation via:

		Vertx vertx = Vertx.vertx();
		SessionStore store = SessionStore.create(vertx, new JsonObject());

To integrate this SessionStore with your application, refer to the [Vert.x Web documenatation](https://vertx.io/docs/vertx-web/java/#_handling_sessions).

e.g.

		SessionHandler sessionHandler = SessionHandler.create(store);
		Router.router(vertx).route().handler(sessionHandler);

## Configuration

Configuration of the distributed SessionStore is supplied via the JsonObject passed to SessionStore.create(...).

e.g.

		{
			"deploymentName": "<1>",
			"granularity": "<2>",
			"marshaller": "<3>",
			"maxActiveSessions": <4>,
			"sessionIdentifierLength": <5>,
			... implementation specific options ...
		}

|#|Property|Description|
|:---|:---|:---|
|<1>|deploymentName|Defines the logical name of the deployment/application.|
|<2>|granularity|Defines the replication granularity of a session. Supported granularities are enumerated by the `org.wildfly.clustering.vertx.web.SessionPersistenceGranularity` enum. `SESSION` will marshall all attributes of a session together preserving any cross-attribute references, while `ATTRIBUTE` will only replicate modified attributes, but will not preserve cross-attribute references.  Default is `ATTRIBUTE`.|
|<3>|marshaller|Specifies the marshaller used to serialize and deserialize session attributes. Supported marshallers are enumerated by the `org.wildfly.clustering.vertx.web.SessionAttributeMarshaller` enum and include: `JAVA`, i.e. Java serialization; `JBOSS`, i.e. JBoss Marshalling; `PROTOSTREAM`, i.e. protobuf. Default marshaller is `JBOSS`.|
|<4>|maxActiveSessions|Defines the maximum number of sessions to retain within the data container, for embedded Infinispan; or within the HotRod near-cache, for a remote Infinispan cluster.  By default, embedded Infinispan will use an unbounded data container, while HotRod will disable its near-cache.|
|<5>|sessionIdentifierLength|Defines the session identifier length. Defaults to 18.|

### Implementation-specific configuration

#### wildfly-clustering-vertx-infinispan-embedded

This implementation stores session attributes and metadata within an embedded Infinispan cache and defines the following additional configuration properties:

		{
			... Generic options ...
			"resource": "<5>"
			"cache": "<6>"
		}

|#|Property|Description|
|:---|:---|:---|
|<6>|resource|Defines the classpath resource name or URL of the Infinispan XML configuration.|
|<7>|cache|Defines the name of the cache configuration from which an application/deployment specific cache will be configured.|

#### wildfly-clustering-vertx-infinispan-remote

This implementation stores session attributes and metadata within a remote Infinispan cluster and defines the following additional configuration properties:

		{
			... Generic options ...
			"uri": "<5>"
			"configuration": "<6>"
			"properties": {
				"name": "value"
			}
		}

|#|Property|Description|
|:---|:---|:---|
|<6>|uri|Defines a HotRod URI, which includes a list of infinispan server instances and any authentication details. For details, see: https://infinispan.org/blog/2020/05/26/hotrod-uri/|
|<7>|configuration|Defines an Infinispan cache configuration (as XML or JSON) to be installed on the server from which the deployment/application specific cache will be created.|
|<8>|properties|Defines a set of adhoc properties used to configure the Infinispan HotRod client. See: https://docs.jboss.org/infinispan/15.1/apidocs/org/infinispan/client/hotrod/configuration/package-summary.html|

### Implementation notes

