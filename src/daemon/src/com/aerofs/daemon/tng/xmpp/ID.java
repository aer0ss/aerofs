/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.base.Base64;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID.ExInvalidID;

import java.util.regex.Pattern;

/**
 * the jid string can be either of the two forms
 * <p/>
 * Form A: <did>@<server>/{u,m} Form B: <chatroom>@c.aerofs.com/<did>
 */

public abstract class ID
{
    private ID()
    {
        // private to enforce uninstantiatibility
        assert false;
    }

    public static String resource(boolean ucast)
    {
        return ucast ? "u" : "m";
    }

    //
    // JID/DID
    //

    public static String did2user(DID did)
    {
        return did.toStringFormal();
    }

    public static DID user2did(String user)
            throws ExFormatError
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
    public static String[] tokenize(String xmpp)
            throws ExFormatError
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
    public static DID jid2did(String xmpp)
            throws ExFormatError
    {
        return jid2did(tokenize(xmpp));
    }

    //
    // MUC identifiers
    //

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

    public static SID muc2sid(String muc)
            throws ExFormatError
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
        return sid.toStringFormal() + '@' + DaemonParam.XMPP.MUC_ADDR;
    }

    //
    // XMPP server credentials
    //

    /**
     * Returns the credentials required to log into the XMPP server
     *
     * @return sha256(scrypt(p|u)|u)
     */
    public static String getShaedXMPP()
    {
        return ShaedXMPPHolder.s_shaedXMPP;
    }

    //
    // constants
    //

    private static class ShaedXMPPHolder
    {
        static final String s_shaedXMPP = Base64.encodeBytes(SecUtil.hash(Cfg.scrypted(),
                XMPP_SERVER_SALT));  // sha256(scrypt(p|u)|XMPP_SERVER_SALT)
    }

    // 64 bytes
    private static final byte[] XMPP_SERVER_SALT = {(byte) 0xcc, (byte) 0xd9, (byte) 0x82,
            (byte) 0x0d, (byte) 0xf2, (byte) 0xf1, (byte) 0x4a, (byte) 0x56, (byte) 0x0a,
            (byte) 0x70, (byte) 0x28, (byte) 0xbe, (byte) 0x91, (byte) 0xd6, (byte) 0xb8,
            (byte) 0x51, (byte) 0x78, (byte) 0x03, (byte) 0xc4, (byte) 0x8f, (byte) 0x30,
            (byte) 0x8b, (byte) 0xdd, (byte) 0xbf, (byte) 0x2d, (byte) 0x80, (byte) 0x45,
            (byte) 0x75, (byte) 0xff, (byte) 0x2d, (byte) 0x4f, (byte) 0x55, (byte) 0x0c,
            (byte) 0x2e, (byte) 0x1b, (byte) 0x2d, (byte) 0x80, (byte) 0x77, (byte) 0x73,
            (byte) 0x95, (byte) 0x25, (byte) 0x7c, (byte) 0xf2, (byte) 0x8e, (byte) 0xa5,
            (byte) 0x49, (byte) 0x5c, (byte) 0xf2, (byte) 0xa6, (byte) 0x4a, (byte) 0x64,
            (byte) 0x31, (byte) 0x3a, (byte) 0xb3, (byte) 0x04, (byte) 0x48, (byte) 0xd7,
            (byte) 0x89, (byte) 0xeb, (byte) 0xd6, (byte) 0x17, (byte) 0x7e, (byte) 0x56,
            (byte) 0x81};
}
