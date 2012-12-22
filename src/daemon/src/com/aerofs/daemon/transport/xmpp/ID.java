package com.aerofs.daemon.transport.xmpp;

import java.util.regex.Pattern;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;

/**
 * the jid string can be either of the two forms
 *
 *  Form A: <did>@<server>/{u,m}
 *  Form B: <chatroom>@c.aerofs.com/<did>
 */

public class ID {

    public static String resource(boolean ucast)
    {
        return ucast ? "u" : "m";
    }

    public static String did2user(DID did)
    {
        return did.toStringFormal();
    }

    public static DID user2did(String user) throws ExFormatError
    {
        return new DID(user);
    }

    /**
     * @return form A jid
     */
    public static String did2jid(DID did, boolean ucast)
    {
        return did2user(did) + '@' + DaemonParam.XMPP.SERVER_DOMAIN +
                '/' + resource(ucast);
    }

    /**
     * @return form B jid
     */
    public static String did2jid(DID did, SID sid)
    {
        return sid2muc(sid) + '/' + did.toStringFormal();
    }

    private static final Pattern SLASH_PATTERN = Pattern.compile("/");

    /**
     * split the string using "/"
     */
    public static String[] tokenize(String xmpp) throws ExFormatError
    {
        String tokens[] = SLASH_PATTERN.split(xmpp);
        if (tokens.length != 2) {
            throw new ExFormatError("wrong # of /'s: " + xmpp);
        }
        return tokens;
    }

    /**
     * convert either form A or form B jid to did
     */
    public static DID jid2did(String xmpp) throws ExFormatError
    {
        return jid2did(tokenize(xmpp));
    }

    public static boolean isMUCAddress(String[] tokens)
    {
        assert tokens.length == 2;
        return tokens[1].length() != 1;
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

    public static SID muc2sid(String muc) throws ExFormatError
    {
        int at = muc.indexOf('@');
        if (at < 0) {
            throw new ExFormatError("not a valid muc name: " + muc);
        }

        // FIXME: we should not use a string store address - instead we should use a binary sid
        return new SID(new UniqueID(muc, 0, at));
    }

    public static String sid2muc(SID sid)
    {
        return sid.toStringFormal() + '@' + DaemonParam.XMPP.MUC_ADDR;
    }

}
