/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.base.C;
import com.aerofs.lib.SecUtil;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class SPParam
{
    // Context attributes.
    public static final String SSMP_CLIENT_ATTRIBUTE = "ssmp_client";

    public static final String SESSION_USER_TRACKER = "session_user_tracker";
    public static final String SESSION_INVALIDATOR = "session_invalidator";
    public static final String SESSION_EXTENDER = "session_extender";

    public static int MAX_GROUP_SIZE = getIntegerProperty("sp.max.membership.group", 50);
    public static int MAX_SHARED_FOLDER_MEMBERS =
            getIntegerProperty("sp.max.membership.sharedfolder", 50);

    // Email related constants.
    public static final String BRAND = getStringProperty("labeling.brand", "AeroFS");
    public static final String EMAIL_FROM_NAME = BRAND;
    // duplicated in S.COPYRIGHT, keep these two strings identical
    // this is duplicated so that SP and sparta will not depend on S which depends on L
    public static final String COPYRIGHT = "2010-2015 Air Computing Inc. All Rights Reserved.";

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

    public static byte[] getShaedSP(byte[] scrypted)
    {
        return SecUtil.hash(scrypted, PASSWD_SALT);
    }

    // SP Constants go here
    public static final long PASSWORD_RESET_TOKEN_VALID_DURATION = 1 * C.DAY;

    // Also see URLs defined in BaseParam.WWW
    public static final String STATIC_ASSETS =
            getStringProperty("sp.param.static_assets", "https://d37fxzwppxbuun.cloudfront.net");

}
