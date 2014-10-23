package com.aerofs.ids.core;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public abstract class Identifiers {

    private static final Logger LOGGER = LoggerFactory.getLogger(Identifiers.class);

    /**
     * UniqueID obtained from generate() are UUID version 4 as specified by RFC 4122
     *
     * To distinguish different subtypes of unique ids we sometimes change the value of the version
     * nibble (4 most significant bits of the 7th byte of the id).
     * The following constants help manipulating the 4 bits in question.
     */
    public static final int VERSION_BYTE = 6;

    public static final int VERSION_MASK = 0xf0;

    public static final int VERSION_SHIFT = 4;

    public static final int NUM_BYTES_IN_IDENTIFIER = 16;

    public static boolean isRootStore(String identifier) {
        return isRootStore(hexDecode(identifier));
    }

    public static boolean isRootStore(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 3;
    }

    public static boolean isSharedFolder(String identifier) {
        return isSharedFolder(hexDecode(identifier));
    }

    public static boolean isSharedFolder(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 0;
    }

    public static String newRandomSharedFolder() {
        return getNibbledRandomBytes(0);
    }

    public static boolean isMountPoint(String identifier) {
        return isMountPoint(hexDecode(identifier));
    }

    public static boolean isMountPoint(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 0;
    }

    public static boolean isObject(String identifier) {
        return isObject(hexDecode(identifier));
    }

    public static boolean isObject(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 4;
    }

    public static String newRandomObject() {
        return getNibbledRandomBytes(4);
    }

    public static boolean isDevice(String identifier) {
        return isDevice(hexDecode(identifier));
    }

    public static boolean isDevice(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 4;
    }

    public static String newRandomDevice() {
        return getNibbledRandomBytes(4);
    }

    private static String getNibbledRandomBytes(int nibbleValue) {
        // create random bytes
        Random random = new Random();
        byte[] bytes = new byte[NUM_BYTES_IN_IDENTIFIER];
        random.nextBytes(bytes);

        // set the version nibble
        byte lobits = (byte) (bytes[VERSION_BYTE] & 0x0f);
        byte hibits = (byte) ((nibbleValue << VERSION_SHIFT) & VERSION_MASK);
        bytes[VERSION_BYTE] = (byte) (hibits | lobits);

        // return the hex-encoded value
        return BaseEncoding.base16().lowerCase().encode(bytes);
    }

    public static int getVersionNibble(byte[] identifier) {
        checkIdentifierLength(identifier);
        return (identifier[VERSION_BYTE] & VERSION_MASK) >> VERSION_SHIFT;
    }

    private static void checkIdentifierLength(byte[] identifier) {
        Preconditions.checkArgument(hasValdiIdentifierLength(identifier), "identifier has invalid length %d", identifier.length);
    }

    public static boolean hasValdiIdentifierLength(byte[] identifier) {
        return identifier.length == NUM_BYTES_IN_IDENTIFIER;
    }

    public static byte[] hexDecode(String identifier) {
        try {
            return BaseEncoding.base16().lowerCase().decode(identifier.toLowerCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("invalid hex-encoded identifier {}", identifier);
            throw new IllegalArgumentException(identifier + " is not a valid identifier");
        }
    }

    private Identifiers() {
        // to prevent instantiation by subclasses
    }
}
