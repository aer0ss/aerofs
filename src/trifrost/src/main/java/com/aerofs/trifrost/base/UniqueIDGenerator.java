package com.aerofs.trifrost.base;

/**
 */
public interface UniqueIDGenerator {
    static UniqueID create() { return new UniqueID(); }

    public char[] generateOneTimeCode();
    public char[] generateDeviceString();
}
