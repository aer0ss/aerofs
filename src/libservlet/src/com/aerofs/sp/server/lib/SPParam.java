/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.labeling.L;
import com.aerofs.lib.C;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.SecUtil;

public class SPParam
{
    // Init parameters.
    public static final String VERKEHR_HOST_INIT_PARAMETER = "verkehr_host";
    public static final String VERKEHR_PUBLISH_PORT_INIT_PARAMETER = "verkehr_publish_port";
    public static final String VERKEHR_ADMIN_PORT_INIT_PARAMETER = "verkehr_admin_port";
    public static final String VERKEHR_CACERT_INIT_PARAMETER = "verkehr_cacert";

    public static final String SP_DATABASE_REFERENCE_PARAMETER = "sp_database_resource_reference";

    // Context attributes.
    public static final String VERKEHR_PUBLISHER_ATTRIBUTE = "verkehr_publisher";
    public static final String VERKEHR_ADMIN_ATTRIBUTE = "verkehr_admin";

    public static final String SESSION_USER_TRACKER = "session_user_tracker";
    public static final String SESSION_INVALIDATOR = "session_invalidator";

    // Verkehr constants.
    public static final long VERKEHR_RECONNECT_DELAY = 5 * C.SEC; // milliseconds
    public static final long VERKEHR_ACK_TIMEOUT = 1 * C.SEC; // milliseconds

    // Email related constants.
    public static final String SP_EMAIL_NAME = L.PRODUCT;
    public static final String SP_NOTIFICATION_SENDER = "sp@aerofs.com";
    public static final String SP_NOTIFICATION_RECEIVER = "team@aerofs.com";

    /**
     * Number of bytes we use for the salt
     */
    public static final byte[] PASSWD_SALT = {
        (byte)0x59, (byte)0xeb, (byte)0x04, (byte)0xb5,
        (byte)0xb7, (byte)0x32, (byte)0x8c, (byte)0xc9,
        (byte)0x92, (byte)0xcd, (byte)0xe4, (byte)0xad,
        (byte)0x8c, (byte)0x95, (byte)0x53, (byte)0xc9,
        (byte)0x3a, (byte)0x2e, (byte)0x46, (byte)0x36,
        (byte)0xf8, (byte)0x65, (byte)0x2e, (byte)0x4e,
        (byte)0x57, (byte)0x3b, (byte)0x44, (byte)0x11,
        (byte)0x13, (byte)0xc0, (byte)0x16, (byte)0xbc,
        (byte)0xec, (byte)0xc9, (byte)0xde, (byte)0x61,
        (byte)0x7b, (byte)0x68, (byte)0xbc, (byte)0x8a,
        (byte)0x8d, (byte)0x1c, (byte)0x23, (byte)0x67,
        (byte)0x96, (byte)0x14, (byte)0x97, (byte)0xdd,
        (byte)0x94, (byte)0x31, (byte)0x41, (byte)0x4d,
        (byte)0x52, (byte)0xa5, (byte)0x05, (byte)0x23,
        (byte)0xa5, (byte)0xb6, (byte)0xc9, (byte)0xb1,
        (byte)0x00, (byte)0xe1, (byte)0xef, (byte)0x20
    };

    // TODO (WW) move it to CredentialUtil?
    public static byte[] getShaedSP(byte[] scrypted)
    {
        return SecUtil.hash(scrypted, PASSWD_SALT);
    }

    // SP Constants go here

    public static final long PASSWORD_RESET_TOKEN_VALID_DURATION = 1 * C.DAY;
    public static final int PASSWORD_RESET_TOKEN_LENGTH = 10;

    private static final String WEB_DOWNLOAD_PARAM_NAME_IC = "a";

    public static String getWebDownloadLink(String signUpCode)
    {
        return SV.DOWNLOAD_LINK + "?" + WEB_DOWNLOAD_PARAM_NAME_IC + "=" + signUpCode;
    }
}
