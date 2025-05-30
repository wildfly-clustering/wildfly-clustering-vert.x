/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.vertx.web.infinispan.embedded;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.infinispan.commons.util.StringPropertyReplacer;
import org.infinispan.configuration.global.TransportConfiguration;
import org.infinispan.remoting.transport.jgroups.JGroupsChannelConfigurator;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.ChannelListener;
import org.jgroups.EmptyMessage;
import org.jgroups.Global;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.blocks.RequestCorrelator.Header;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.conf.ProtocolConfiguration;
import org.jgroups.conf.ProtocolStackConfigurator;
import org.jgroups.conf.XmlConfigurator;
import org.jgroups.fork.UnknownForkHandler;
import org.jgroups.protocols.FORK;
import org.jgroups.protocols.TP;
import org.jgroups.stack.Configurator;
import org.jgroups.stack.Protocol;
import org.jgroups.util.DefaultThreadFactory;
import org.jgroups.util.SocketFactory;
import org.jgroups.util.StackType;
import org.jgroups.util.Util;

/**
 * @author Paul Ferraro
 */
public class JChannelConfigurator implements JGroupsChannelConfigurator {

	static final String DEFAULT_JGROUPS_RESOURCE = "default-configs/default-jgroups-udp.xml";
	static final ByteBuffer UNKNOWN_FORK_RESPONSE = ByteBuffer.allocate(0);

	private final String name;
	private final ProtocolStackConfigurator configurator;
	private final List<ChannelListener> listeners = new LinkedList<>();

	public JChannelConfigurator(TransportConfiguration transport, ClassLoader loader) throws IOException {
		this.name = transport.stack();
		this.configurator = getProtocolStackConfigurator(transport, loader);
	}

	private static ProtocolStackConfigurator getProtocolStackConfigurator(TransportConfiguration transport, ClassLoader loader) throws IOException {
		Properties properties = transport.properties();
		if (properties.containsKey(JGroupsTransport.CHANNEL_CONFIGURATOR)) {
			return (JGroupsChannelConfigurator) properties.get(JGroupsTransport.CHANNEL_CONFIGURATOR);
		}
		if (properties.containsKey(JGroupsTransport.CONFIGURATION_XML)) {
			String xml = properties.getProperty(JGroupsTransport.CONFIGURATION_XML);
			try (InputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
				return XmlConfigurator.getInstance(input);
			}
		}
		String location = properties.getProperty(JGroupsTransport.CONFIGURATION_FILE, DEFAULT_JGROUPS_RESOURCE);
		URL resource = loader.getResource(location);
		if (resource == null) {
			throw new FileNotFoundException(location);
		}
		try (InputStream input = resource.openStream()) {
			return XmlConfigurator.getInstance(input);
		}
	}

	@Override
	public String getProtocolStackString() {
		return this.configurator.getProtocolStackString();
	}

	@Override
	public List<ProtocolConfiguration> getProtocolStack() {
		return this.configurator.getProtocolStack();
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public JChannel createChannel(String name) throws Exception {
		FORK fork = new FORK();
		fork.setUnknownForkHandler(new UnknownForkHandler() {
			private final short id = ClassConfigurator.getProtocolId(RequestCorrelator.class);

			@Override
			public Object handleUnknownForkStack(Message message, String forkStackId) {
				return this.handle(message);
			}

			@Override
			public Object handleUnknownForkChannel(Message message, String forkChannelId) {
				return this.handle(message);
			}

			private Object handle(Message message) {
				Header header = (Header) message.getHeader(this.id);
				// If this is a request expecting a response, don't leave the requester hanging - send an identifiable response on which it can filter
				if ((header != null) && (header.type == Header.REQ) && header.rspExpected()) {
					Message response = new EmptyMessage(message.src()).setFlag(message.getFlags(), false).clearFlag(Message.Flag.RSVP);
					if (message.getDest() != null) {
						response.src(message.getDest());
					}

					response.putHeader(FORK.ID, message.getHeader(FORK.ID));
					response.putHeader(this.id, new Header(Header.RSP, header.req_id, header.corrId));

					fork.getProtocolStack().getChannel().down(response);
				}
				return null;
			}
		});
		List<ProtocolConfiguration> configurations = this.getProtocolStack();
		List<Protocol> protocols = new ArrayList<>(configurations.size() + 1);
		Properties properties = System.getProperties();
		for (ProtocolConfiguration configuration : configurations) {
			String protocolName = configuration.getProtocolName();
			String className = protocolName.startsWith(Global.PREFIX) ? protocolName : Global.PREFIX + protocolName;
			try {
				Class<? extends Protocol> protocolClass = Protocol.class.getClassLoader().loadClass(className).asSubclass(Protocol.class);
				Protocol protocol = protocolClass.getConstructor().newInstance();
				StringPropertyReplacer.replaceProperties(configuration.getProperties(), properties);
				StackType type = Util.getIpStackType();
				Configurator.initializeAttrs(protocol, configuration, type);
				protocols.add(protocol);
			} catch (InstantiationException | IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		// Add implicit FORK to the top of the stack
		protocols.add(fork);
		TP transport = (TP) protocols.get(0);
		transport.setThreadFactory(new ClassLoaderThreadFactory(new DefaultThreadFactory("jgroups", false, true), JChannelConfigurator.class.getClassLoader()));

		JChannel channel = new JChannel(protocols);
		this.listeners.forEach(channel::addChannelListener);
		return channel;
	}

	@Override
	public void setSocketFactory(SocketFactory socketFactory) {
	}

	@Override
	public void setDataSource(DataSource dataSource) {
	}
}
