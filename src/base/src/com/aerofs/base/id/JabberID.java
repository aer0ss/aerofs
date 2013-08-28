/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import org.slf4j.Logger;

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
    private final static Logger l = Loggers.getLogger(JabberID.class);
    private final static String MOBILE_PREFIX = "mobile_";

    private JabberID()
    {
            // private to enforce uninstantiability
    }

    public static String did2user(DID did)
    {
        return did.toStringFormal();
    }

    public static DID user2did(String user) throws ExFormatError
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
    public static String did2FormAJid(DID did, String xmppTransportId)
    {
        return did2user(did) + '@' + XMPP.getServerDomain() + '/' + xmppTransportId;
    }

    /**
     * This is the nickname we use when joining a multicast room. It is very similar to the Form A
     * jid, except instead of having the xmpp transport ID as a resource we append it to the did
     */
    public static String getMUCRoomNickname(DID did, String xmppTransportId)
    {
        return JabberID.did2user(did) + "-" + xmppTransportId;
    }

    /**
     * @return form B jid for multicast connections (this is the jid that is reported for all members
     * in a MUC)
     */
    public static String did2FormBJid(DID did, SID sid, String xmppTransportId)
    {
        return sid2muc(sid) + '/' + getMUCRoomNickname(did, xmppTransportId);
    }

    private static final Pattern SLASH_PATTERN = Pattern.compile("/");
    private static final Pattern DASH_PATTERN = Pattern.compile("-");

    /**
     * split the jid using "/" and "-" (form A (jingle), form B)
     */
    public static String[] tokenize(String jid) throws ExFormatError
    {
        // split using "/"

        String components[] = SLASH_PATTERN.split(jid);
        if (components.length != 2) {
            throw new ExFormatError("wrong # of /'s: " + jid);
        }

        // split using "-"

        String[] didAndXmppTransportId = DASH_PATTERN.split(components[1]);

        String[] tokens = new String[1 + didAndXmppTransportId.length];
        tokens[0] = components[0];

        arraycopy(didAndXmppTransportId, 0, tokens, 1, didAndXmppTransportId.length);

        if (l.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(128);
            for (String token : tokens) {
                sb.append(token).append("|");
            }

            l.debug("j:{} t:{}", jid, sb.toString());
        }

        return tokens;
    }

    /**
     * convert either form A or form B jid to did
     */
    public static DID jid2did(String jid) throws ExFormatError
    {
        return jid2did(tokenize(jid));
    }

    public static DID jid2did(String[] tokens)
        throws ExFormatError
    {
        if (isMUCAddress(tokens)) {
            // format B
            return new DID(tokens[1]);

        } else {
            // format A
            int at = tokens[0].indexOf('@');
            if (at < 0) throw new ExFormatError("@ not found");
            return new DID(tokens[0], 0, at);
        }
    }

    public static boolean isMUCAddress(String[] tokens)
    {
        checkArgument(tokens.length >= 2, "insufficient tokens len:" + tokens.length);
        return tokens[1].length() != 1;
    }

    public static SID muc2sid(String muc) throws ExFormatError
    {
        int at = muc.indexOf('@');
        if (at < 0) {
            throw new ExFormatError("not a valid muc name: " + muc);
        }

        // FIXME: we should not use a string store address - instead we should use a binary sid
        try {
            return new SID(muc, 0, at);
        } catch (ExInvalidID e) {
            // TODO: propagate ExInvalidID upwards for special handling?
            throw new ExFormatError("invalid SID");
        }
    }

    public static String sid2muc(SID sid)
    {
        return sid.toStringFormal() + '@' + XMPP.getMucAddress();
    }
}
