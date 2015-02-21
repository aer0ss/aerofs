package com.aerofs.ids;

// TODO (MP) remove public constructors use fromExternal() fromInternal().
/**
 * AeroFS device identifier
 */
public class DID extends UniqueID {

    protected static final int MDID_VERSION_NIBBLE = 8;

    public DID(UniqueID id) {
        super(id);
    }

    public DID(byte[] bs) {
        super(bs);
    }

    public DID(String str, int start, int end) throws ExInvalidID {
        super(str, start, end);
    }

    public DID(String str) throws ExInvalidID {
        super(str);
    }

    public static DID generate() {
        return new DID(UniqueID.generate());
    }

    public static DID fromExternal(byte[] bs) throws ExInvalidID {
        if (bs.length != UniqueID.LENGTH) {
            throw new ExInvalidID();
        }
        return new DID(bs);
    }

    public static DID fromInternal(byte[] bs) {
        return new DID(bs);
    }

    public boolean isMobileDevice() {
        return getVersionNibble() == MDID_VERSION_NIBBLE;
    }
}
