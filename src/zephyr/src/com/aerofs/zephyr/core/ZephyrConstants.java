/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.zephyr.core;

import com.aerofs.zephyr.Constants;

public class ZephyrConstants
{
    ZephyrConstants() {}

    public static final String ZEPHYR_HOST = "zephyr.aerofs.com";

    public static final int ZEPHYR_PORT = 443;

    /**
     * magic number identifying incoming messages
     */
    public static final byte[] ZEPHYR_MAGIC = Constants.ZEPHYR_MAGIC;

    /**
     * Length of the Zephyr Server registration payload (it's a zid)
     */
    public static final int ZEPHYR_REG_PAYLOAD_LEN = Constants.ZEPHYR_REG_PAYLOAD_LEN;

    /**
     * Length of the Zephyr bind message payload
     */
    public static final int ZEPHYR_BIND_PAYLOAD_LEN = Constants.ZEPHYR_BIND_PAYLOAD_LEN;

    public static final int ZEPHYR_ID_LEN = 4;

    public static final int ZEPHYR_INVALID_CHAN_ID = Constants.ZEPHYR_INVALID_CHAN_ID;
}
