/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.xray;

import com.aerofs.base.C;

import java.nio.ByteOrder;

/**
 * Constants required by both Zephyr and Zephyr clients
 */
public final class Constants
{
    /** byte-ordering for all zephyr messages */
    public static final ByteOrder ZEPHYR_MSG_BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    /** magic number identifying incoming messages */
    public static final byte[] ZEPHYR_MAGIC = {(byte)0x82, (byte)0x96, (byte)0x44, (byte)0xa1};

    /** length of the header for any message directed to/from zephyr */
    public static final int ZEPHYR_SERVER_HDR_LEN = ZEPHYR_MAGIC.length + C.INTEGER_SIZE; // 8

    /** length of the header for any message to/from a client via zephyr */
    public static final int ZEPHYR_CLIENT_HDR_LEN = ZEPHYR_SERVER_HDR_LEN;

    /** Length of the Zephyr Server registration payload (it's a zid) */
    public static final int ZEPHYR_REG_PAYLOAD_LEN = C.INTEGER_SIZE;

    /** zephyr registration message length */
    public static final int ZEPHYR_REG_MSG_LEN = ZEPHYR_SERVER_HDR_LEN + ZEPHYR_REG_PAYLOAD_LEN; // 12

    /** Length of the Zephyr bind message payload */
    public static final int ZEPHYR_BIND_PAYLOAD_LEN = C.INTEGER_SIZE;

    /** zephyr client bind message length */
    public static final int ZEPHYR_BIND_MSG_LEN = ZEPHYR_SERVER_HDR_LEN + ZEPHYR_BIND_PAYLOAD_LEN; // 12

    /** invalid zephyr channel id */
    public static final int ZEPHYR_INVALID_CHAN_ID = -1;
}
