/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.ids.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.j.Jid;

abstract class JingleUtils
{
    private static final String JINGLE_RESOURCE_NAME = "u";

    private JingleUtils() {} // private to prevent instantiation

    /**
     * Convert a DID> to an XMPP JID valid on the AeroFS XMPP server
     *
     * @param did {@link com.aerofs.base.id.DID} to convert
     * @param xmppServerDomain domain to which the resulting {@link Jid} belongs
     * @return a valid XMPP user id of the form: {$user}@{$domain}/{$resource}
     */
    static Jid did2jid(DID did, String xmppServerDomain)
    {
        return new Jid(JabberID.did2user(did), xmppServerDomain, JINGLE_RESOURCE_NAME);
    }

    static DID jid2did(Jid jid) throws ExInvalidID
    {
        return JabberID.user2did(jid.node());
    }

    static DID jid2didNoThrow(Jid jid)
    {
        try {
            return JabberID.user2did(jid.node());
        } catch (ExInvalidID e) {
            return new DID(DID.ZERO);
        }
    }
}
