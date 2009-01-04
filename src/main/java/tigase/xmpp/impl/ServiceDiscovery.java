/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2007 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.xmpp.impl;

import java.util.Arrays;
import java.util.Queue;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import tigase.server.Command;
import tigase.server.Packet;
import tigase.disco.XMPPServiceCollector;
import tigase.util.JIDUtils;
import tigase.util.ElementUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPProcessorIfc;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.XMPPException;
import tigase.db.NonAuthUserRepository;

/**
 * Implementation of JEP-030.
 *
 *
 * Created: Mon Mar 27 20:45:36 2006
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public abstract class ServiceDiscovery extends XMPPProcessor
	implements XMPPProcessorIfc {

	private static final Logger log =
    Logger.getLogger("tigase.xmpp.impl.ServiceDiscovery");

	private static final String ID = "disco";
  private static final String[] ELEMENTS =
	{"query", "query", "query"};
  private static final String[] XMLNSS = {
    XMPPServiceCollector.INFO_XMLNS,
		XMPPServiceCollector.ITEMS_XMLNS,
		Command.XMLNS
	};
  private static final Element[] DISCO_FEATURES = {
    new Element("feature",
			new String[] {"var"},
			new String[] {XMPPServiceCollector.INFO_XMLNS}),
		new Element("feature",
			new String[] {"var"},
			new String[] {XMPPServiceCollector.ITEMS_XMLNS})
	};

	public String id() { return ID; }

	public String[] supElements()	{ return ELEMENTS; }

  public String[] supNamespaces()	{ return XMLNSS; }

  public Element[] supDiscoFeatures(final XMPPResourceConnection session)
	{ return DISCO_FEATURES; }

	public void process(final Packet packet, final XMPPResourceConnection session,
		final NonAuthUserRepository repo, final Queue<Packet> results,
		final Map<String, Object> settings)
		throws XMPPException {

		if (session == null) {
			return;
		} // end of if (session == null)

		try {
			// Maybe it is message to admininstrator:
			String nodeId = null;
			String nodeNick = null;
			if (packet.getElemTo() != null) {
				nodeId = JIDUtils.getNodeID(packet.getElemTo());
				nodeNick = JIDUtils.getNodeNick(packet.getElemTo());
			} // end of if (packet.getElemTo() != null)

			if (packet.isCommand()) {
				if (packet.getCommand() == Command.GETDISCO
					&& packet.getType() == StanzaType.result) {
					// Send it back to user.
					Element query = Command.getData(packet, "query", null);
					Element iq =
						ElementUtils.createIqQuery(session.getDomain(), session.getJID(),
							StanzaType.result, packet.getElemId(), query);
					Packet result = new Packet(iq);
					result.setTo(session.getConnectionId());
					result.getElement().setAttribute("from",
						Command.getFieldValue(packet,	"jid"));
					results.offer(result);
					return;
				} else {
					return;
				}
			}

			// If ID part of user account contains only host name
			// and this is local domain it is message to admin
			if (nodeId == null || nodeId.equals("")
				|| (nodeNick == null && nodeId.endsWith(session.getDomain()))) {
				Element query = packet.getElement().getChild("query");
				Packet discoCommand = Command.GETDISCO.getPacket(session.getJID(),
					session.getDomain(), StanzaType.get, packet.getElemId(), 
					Command.DataType.submit);
				Command.addFieldValue(discoCommand, "xmlns", query.getXMLNS());
				Command.addFieldValue(discoCommand, "jid", packet.getElemTo());
				if (query.getAttribute("node") != null) {
					Command.addFieldValue(discoCommand, "node", query.getAttribute("node"));
				} // end of if (query.getAttribute("node") != null)
				results.offer(discoCommand);
				return;
			}

			if (nodeId.equals(session.getUserId())) {
				// Yes this is message to 'this' client
				Element elem = packet.getElement().clone();
				Packet result = new Packet(elem);
				result.setTo(session.getConnectionId(packet.getElemTo()));
				result.setFrom(packet.getTo());
				results.offer(result);
			} else {
				// This is message to some other client so I need to
				// set proper 'from' attribute whatever it is set to now.
				// Actually processor should not modify request but in this
				// case it is absolutely safe and recommended to set 'from'
				// attribute
				Element result = packet.getElement().clone();
				// Not needed anymore. Packet filter does it for all stanzas.
// 				// According to spec we must set proper FROM attribute
// 				result.setAttribute("from", session.getJID());
				results.offer(new Packet(result));
			} // end of else
		} catch (NotAuthorizedException e) {
      log.warning(
				"Received stats request but user session is not authorized yet: " +
        packet.getStringData());
			results.offer(Authorization.NOT_AUTHORIZED.getResponseMessage(packet,
					"You must authorize session first.", true));
		} // end of try-catch
	}

} // ServiceDiscovery
