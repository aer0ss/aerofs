/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;

import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.System.arraycopy;

/**
 * the jid string can be either of the two forms
 *
 *  Form A: <did>@<server>/{u,m}
 *  Form B: <chatroom>@c.aerofs.com/<did>
 */

public abstract class JabberID
{
    private final static String MOBILE_PREFIX = "mobile_";

    private JabberID()
    {
            // private to enforce uninstantiability
    }

    public static String did2user(DID did)
    {
        return did.toStringFormal();
    }

    public static DID user2did(String user) throws ExInvalidID
    {
        return new DID(user);
    }

    public static String did2mobileUser(DID did)
    {
        return MOBILE_PREFIX + did2user(did);
    }

    public static boolean isMobileUser(String user)
    {
        return user != null && user.startsWith(MOBILE_PREFIX);
    }

    /**
     * @return form A jid for multicast connections
     * This is the jid that we use when connecting to the XMPP server
     */
    public static String did2FormAJid(DID did, String xmppServerDomain, String xmppTransportId)
    {
        return String.format("%s@%s/%s", did2user(did), xmppServerDomain, xmppTransportId);
    }

    public static String did2BareJid(DID did, String xmppServerDomain)
    {
        return String.format("%s@%s", did2user(did), xmppServerDomain);
    }

    /**
     * This is the nickname we use when joining a multicast room. It is very similar to the Form A
     * jid, except instead of having the xmpp transport ID as a resource we append it to the did
     */
    public static String getMUCRoomNickname(DID did, String xmppTransportId)
    {
        return JabberID.did2user(did) + "-" + xmppTransportId;
    }

    private static final Pattern SLASH_PATTERN = Pattern.compile("/");
    private static final Pattern DASH_PATTERN = Pattern.compile("-");

    /**
     * split the jid using "/" and "-" (form A (jingle), form B)
     */
    public static String[] tokenize(String jid) throws ExInvalidID
    {
        // split using "/"

        String components[] = SLASH_PATTERN.split(jid);
        if (components.length != 2) {
            throw new ExInvalidID("wrong # of /'s: " + jid);
        }

        // split using "-"

        String[] didAndXmppTransportId = DASH_PATTERN.split(components[1]);

        String[] tokens = new String[1 + didAndXmppTransportId.length];
        tokens[0] = components[0];

        arraycopy(didAndXmppTransportId, 0, tokens, 1, didAndXmppTransportId.length);

        return tokens;
    }

    /**
     * convert either form A or form B jid to did
     */
    public static DID jid2did(String jid, String xmppServerDomain) throws ExInvalidID
    {
        return jid2did(tokenize(jid), xmppServerDomain);
    }

    public static DID jid2did(String[] tokens, String xmppServerDomain)
        throws ExInvalidID
    {
        if (isMUCAddress(tokens, xmppServerDomain)) {
            // form B
            return new DID(tokens[1]);
        } else {
            // form A
            int at = tokens[0].indexOf('@');
            if (at < 0) throw new ExInvalidID("@ not found");
            return new DID(tokens[0], 0, at);
        }
    }

    public static boolean isMUCAddress(String[] tokens, String xmppServerDomain)
    {
        checkArgument(tokens.length >= 1);
        return tokens[0].contains(getConferenceAddress(xmppServerDomain));
    }

    public static SID muc2sid(String muc) throws ExInvalidID
    {
        int at = muc.indexOf('@');
        if (at < 0) {
            throw new ExInvalidID("not a valid muc name: " + muc);
        }

        // FIXME: we should not use a string store address - instead we should use a binary sid
        try {
            return new SID(muc, 0, at);
        } catch (ExInvalidID e) {
            throw new ExInvalidID("invalid SID muc:" + muc);
        }
    }

    public static String sid2muc(SID sid, String xmppServerDomain)
    {
        return String.format("%s@%s", sid.toStringFormal(), getConferenceAddress(xmppServerDomain));
    }

    private static String getConferenceAddress(String xmppServerDomain)
    {
        return String.format("c.%s", xmppServerDomain);
    }
}
