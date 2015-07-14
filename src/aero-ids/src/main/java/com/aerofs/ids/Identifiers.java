package com.aerofs.ids;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
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

    public static boolean isRootStore(UniqueID identifier) {
        return isRootStore(identifier.getBytes());
    }

    public static boolean isRootStore(String identifier) {
        return isRootStore(hexDecode(identifier));
    }

    public static boolean isRootStore(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 3;
    }

    public static boolean isSharedFolder(UniqueID identifier) {
        return isSharedFolder(identifier.getBytes());
    }

    public static boolean isSharedFolder(String identifier) {
        return isSharedFolder(hexDecode(identifier));
    }

    public static boolean isSharedFolder(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 0;
    }

    public static String newRandomSharedFolder() {
        return SID.generate().toStringFormal();
    }

    public static boolean isMountPoint(UniqueID identifier) {
        return isMountPoint(identifier.getBytes());
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
        return OID.generate().toStringFormal();
    }

    public static boolean isDevice(String identifier) {
        return isDevice(hexDecode(identifier));
    }

    public static boolean isDevice(byte[] identifier) {
        int versionNibble = getVersionNibble(identifier);
        return versionNibble == 4;
    }

    public static String newRandomDevice() {
        return DID.generate().toStringFormal();
    }

    public static int getVersionNibble(byte[] identifier) {
        checkIdentifierLength(identifier);
        return UniqueID.getVersionNibble(identifier);
    }

    private static void checkIdentifierLength(byte[] identifier) {
        Preconditions.checkArgument(hasValidIdentifierLength(identifier), "identifier has invalid length %d", identifier.length);
    }

    public static boolean hasValidIdentifierLength(byte[] identifier) {
        return identifier.length == UniqueID.LENGTH;
    }

    public static byte[] hexDecode(String identifier) {
        try {
            return BaseEncoding.base16().lowerCase().decode(identifier.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("invalid hex-encoded identifier {}", identifier);
            throw new IllegalArgumentException(identifier + " is not a valid identifier");
        }
    }

    private Identifiers() {
        // to prevent instantiation by subclasses
    }
}
