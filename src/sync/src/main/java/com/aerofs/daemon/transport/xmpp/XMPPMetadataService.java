package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.xmpp.presence.XMPPvCard;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.slf4j.Logger;

/**
 * Service managing metadata linked to Jabber accounts
 */
public class XMPPMetadataService
{
    private static final Logger l = Loggers.getLogger(XMPPMetadataService.class);
    final XMPPConnection _xmppConnection;

    public XMPPMetadataService(final XMPPConnection connection)
    {
        _xmppConnection = connection;
    }

    /**
     * Retrieve a vCard metadata for a given JID
     * @param jid the JID of the user we want the metadata
     * @return The Metadata String
     * @throws XMPPException
     */
    public String getRaw(String jid) throws XMPPException
    {
        XMPPvCard card = new XMPPvCard();
        // Read the given vCard
        card.load(_xmppConnection, jid);
        return card.readMetadata();
    }

    /**
     * Retrieve a vCard metadata for a given JID
     *
     * @param jid the JID of the user we want the metadata
     * @return The Metadata String, or an empty string if an error occured
     */
    public String get(String jid)
    {
        try {
            return getRaw(jid);
        } catch (XMPPException e) {
            l.warn("Unable to retrieve the vCard for JID {}: {}", jid, e.getMessage());
            return "";
        }
    }
}
