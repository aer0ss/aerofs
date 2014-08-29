package com.aerofs.base;

/**
 * C: Physical constants
 */
public class C
{
    // Time
    public static final long NSEC_PER_MSEC = 1_000_000L;
    public static final long SEC = 1000;
    public static final long MIN = 60 * SEC;
    public static final long HOUR = 60 * MIN;
    public static final long DAY = 24 * HOUR;
    public static final long WEEK = 7 * DAY;
    public static final long YEAR = 365 * DAY;

    // Sizes
    public static final int KB = 1024;
    public static final int MB = 1024 * KB;
    public static final long GB = 1024 * MB;

    // This is to avoid the common mistake of treating Integer.SIZE as bytes instead of bits
    public static final int INTEGER_SIZE = Integer.SIZE / Byte.SIZE;
    public static final int LONG_SIZE = Long.SIZE / Byte.SIZE;
}
