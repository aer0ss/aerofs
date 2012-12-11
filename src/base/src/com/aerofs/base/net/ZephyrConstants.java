/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import java.net.InetSocketAddress;

public class ZephyrConstants
{
    ZephyrConstants() {}

    public static final String ZEPHYR_HOST = "zephyr.aerofs.com";

    public static final int ZEPHYR_PORT = 443;

    public static final InetSocketAddress ZEPHYR_ADDRESS =
            InetSocketAddress.createUnresolved(ZEPHYR_HOST, ZEPHYR_PORT);

    public static final String ZEPHYR_STAGING_HOST = "staging.aerofs.com";

    public static final int ZEPHYR_STAGING_PORT = 8888;

    public static final InetSocketAddress ZEPHYR_STAGING_ADDRESS =
            InetSocketAddress.createUnresolved(ZEPHYR_STAGING_HOST, ZEPHYR_STAGING_PORT);

    /**
     * magic number identifying incoming messages
     */
    public static final byte[] ZEPHYR_MAGIC = {(byte)0x82, (byte)0x96, (byte)0x44, (byte)0xa1};

    /**
     * Length of the Zephyr Server registration payload (it's a zid)
     */
    public static final int ZEPHYR_REG_PAYLOAD_LEN = 4;

    /**
     * Length of the Zephyr bind message payload
     */
    public static final int ZEPHYR_BIND_PAYLOAD_LEN = 4;

    /**
     * Length of a zid
     */
    public static final int ZEPHYR_ID_LEN = 4;

    /**
     * Invalid zid
     */
    public static final int ZEPHYR_INVALID_CHAN_ID = -1;
}
