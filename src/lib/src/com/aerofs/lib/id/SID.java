package com.aerofs.lib.id;

import com.aerofs.lib.C;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExFormatError;
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
    private String _str;

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

    private void assertIsValid()
    {
        int v = getVersionNibble(getBytes());
        assert (v == 3 || v == 0) : toStringFormal();
    }

    public boolean isRoot()
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
        byte[] bs = SecUtil.newMessageDigestMD5().digest(oid.getBytes());
        setVersionNibble(bs, 0);
        return new SID(bs);
    }

    /**
     * Translate an arbitrary folder's OID into the SID of the store to which this folder converts.
     */
    public static SID folderOID2convertedStoreSID(OID oid)
    {
        assert !oid.isRoot() && !oid.isTrash() && !oid.isAnchor() : oid.toStringFormal();
        byte[] bs = Arrays.copyOf(oid.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 0);
        return new SID(bs);
    }

    /**
     * Translate an store SID to the original OID of the folder it was converted from.
     */
    public static OID convertedStoreSID2folderOID(SID sid)
    {
        assert !sid.isRoot() : sid.toStringFormal();
        byte[] bs = Arrays.copyOf(sid.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 4);
        return new OID(bs);
    }

    /**
     * Translate a store SID to the OID of the anchor that holds the store
     */
    public static OID storeSID2anchorOID(SID sid)
    {
        assert !sid.isRoot() : sid.toStringFormal();
        return new OID(sid.getBytes());
    }

    /**
     * Translate the OID of an anchor to the SID of the store that the anchor
     * holds
     */
    public static SID anchorOID2storeSID(OID oid)
    {
        assert oid.isAnchor() : oid.toStringFormal();
        return new SID(oid.getBytes());
    }

    /**
     * Translate a user ID to the SID of the user's root store
     */
    public static SID rootSID(UserID userId)
    {
        MessageDigest md = SecUtil.newMessageDigestMD5();
        md.update(Util.string2utf(userId.toString()));
        byte[] bs = md.digest(C.ROOT_SID_SALT);
        setVersionNibble(bs, 3);
        return new SID(bs);
    }

    @Override
    public String toString()
    {
        if (_str == null) {
            StringBuilder sb = new StringBuilder();

            sb.append('$');
            for (int i = 0; i < 2; i++) {
                sb.append(String.format("%1$02x", getBytes()[i]));
            }
            sb.append('$');

            _str = sb.toString();
        }

        return _str;
    }
}
