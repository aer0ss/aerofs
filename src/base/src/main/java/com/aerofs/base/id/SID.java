package com.aerofs.base.id;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExFormatError;
import com.google.protobuf.ByteString;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Store IDs
 *
 * Non-anchor OIDs are guaranteed to be version 4 UUIDs (see UniqueID.generate()) and we abuse some
 * of the fixed bits specified by RFC 4122 to distinguish between various subtypes of OID and SID
 *
 * Specifically we need to:
 *   - maintain an efficient bidirectional mapping between folder OID and corresponding anchor OID
 *   - avoid conflicts between non-anchor OIDs and anchor OIDs
 *   - avoid conflicts between non-root SIDs and root SIDs
 *
 * non-anchor OID: vanilla v4 UUID
 *     anchor OID: original folder OID with version nibble set to 0
 *   non-root SID: same as corresponding anchor OID
 *       root SID: v3-like UUID (i.e. salted MD5 of user id with version nibble set to 3)
 */
public class SID extends UniqueID
{
    private static final byte[] ROOT_SID_SALT = new byte[]
            { (byte) 0x07, (byte) 0x24, (byte) 0xF1, (byte) 0x37 };

    public SID(ByteString bstr)
    {
        super(bstr);
        assertIsValid();
    }

    public SID(UniqueID id)
    {
        super(id);
        assertIsValid();

        // should use one of the conversion methods below to convert between
        // OID and SID
        assert !(id instanceof OID);
    }

    public SID(String str, int start, int end) throws ExFormatError, ExInvalidID
    {
        super(str, start, end);

        if (!isValid()) throw new ExInvalidID();
    }

    public SID(byte[] bs)
    {
        super(bs);
        assertIsValid();
    }

    public SID(String str) throws ExFormatError
    {
        super(str);
        assertIsValid();
    }

    private boolean isValid()
    {
        int v = getVersionNibble(getBytes());
        return (v == 3 || v == 0);
    }

    private void assertIsValid()
    {
        assert isValid() : toStringFormal();
    }

    public boolean isUserRoot()
    {
        return getVersionNibble(getBytes()) == 3;
    }

    /**
     * For testing purpose, as structural restrictions prevent the following:
     *      new SID(UniqueID.generate())
     */
    public static SID generate()
    {
        return folderOID2convertedStoreSID(new OID(UniqueID.generate()));
    }

    /**
     * DEPRECATED: only used in StoreCreator to ease transition
     */
    public static SID folderOID2legacyConvertedStoreSID(OID oid)
    {
        assert !oid.isRoot() && !oid.isTrash() && !oid.isAnchor() : oid.toStringFormal();
        byte[] bs = BaseSecUtil.newMessageDigestMD5().digest(oid.getBytes());
        setVersionNibble(bs, 0);
        SID sid = new SID(bs);
        assert !sid.isUserRoot();
        return sid;
    }

    /**
     * Translate an arbitrary folder's OID into the SID of the store to which this folder converts.
     */
    public static SID folderOID2convertedStoreSID(OID oid)
    {
        assert !oid.isRoot() && !oid.isTrash() && !oid.isAnchor() : oid.toStringFormal();
        byte[] bs = Arrays.copyOf(oid.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 0);
        SID sid = new SID(bs);
        assert !sid.isUserRoot();
        return sid;
    }

    /**
     * Translate an anchor OID to the original OID of the folder it was converted from.
     */
    public static OID anchorOID2folderOID(OID anchor)
    {
        assert anchor.isAnchor() : anchor.toStringFormal();
        byte[] bs = Arrays.copyOf(anchor.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 4);
        OID oid = new OID(bs);
        assert !oid.isAnchor();
        return oid;
    }

    /**
     * Translate a store SID to the original OID of the folder it was converted from.
     */
    public static OID convertedStoreSID2folderOID(SID sid)
    {
        assert !sid.isUserRoot() : sid.toStringFormal();
        byte[] bs = Arrays.copyOf(sid.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 4);
        OID oid = new OID(bs);
        assert !oid.isAnchor();
        return oid;
    }

    /**
     * Translate a store SID to the OID of the anchor that holds the store
     */
    public static OID storeSID2anchorOID(SID sid)
    {
        assert !sid.isUserRoot() : sid.toStringFormal();
        OID oid = new OID(sid.getBytes());
        assert oid.isAnchor();
        return oid;
    }

    /**
     * Translate the OID of an anchor to the SID of the store that the anchor
     * holds
     */
    public static SID anchorOID2storeSID(OID oid)
    {
        assert oid.isAnchor() : oid.toStringFormal();
        SID sid = new SID(oid.getBytes());
        assert !sid.isUserRoot();
        return sid;
    }

    /**
     * Translate a user ID to the SID of the user's root store
     */
    public static SID rootSID(UserID userId)
    {
        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        md.update(BaseUtil.string2utf(userId.getString()));
        byte[] bs = md.digest(ROOT_SID_SALT);
        setVersionNibble(bs, 3);
        SID sid = new SID(bs);
        assert sid.isUserRoot();
        return sid;
    }

    @Override
    public String toString()
    {
        return toStringImpl('$', 2, '$');
    }

    public static SID fromStringFormal(String hex) throws ExFormatError
    {
        return new SID(UniqueID.fromStringFormal(hex));
    }
}
