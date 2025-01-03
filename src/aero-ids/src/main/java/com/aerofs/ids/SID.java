package com.aerofs.ids;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

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

    private static MessageDigest newMessageDigestMD5()
    {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    public SID(UniqueID id)
    {
        super(id);
        assertIsValid();

        // should use one of the conversion methods below to convert between
        // OID and SID
        checkState(!(id instanceof OID));
    }

    public SID(String str, int start, int end) throws ExInvalidID
    {
        super(str, start, end);

        if (!isValid()) throw new ExInvalidID();
    }

    public SID(byte[] bs)
    {
        super(bs);
        assertIsValid();
    }

    public SID(String str) throws ExInvalidID
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
        checkState(isValid(), this);
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
     * Translate an arbitrary folder's OID into the SID of the store to which this folder converts.
     */
    public static SID folderOID2convertedStoreSID(OID oid)
    {
        checkArgument(!oid.isRoot() && !oid.isTrash() && !oid.isAnchor(), oid);
        byte[] bs = Arrays.copyOf(oid.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 0);
        SID sid = new SID(bs);
        checkState(!sid.isUserRoot());
        return sid;
    }

    /**
     * Translate an arbitrary folder's OID into the anchor OID of the store to which this folder converts.
     */
    public static OID folderOID2convertedAnchorOID(OID oid)
    {
        return storeSID2anchorOID(folderOID2convertedStoreSID(oid));
    }

    /**
     * Translate an anchor OID to the original OID of the folder it was converted from.
     */
    public static OID anchorOID2folderOID(OID anchor)
    {
        checkArgument(anchor.isAnchor(), anchor);
        byte[] bs = Arrays.copyOf(anchor.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 4);
        OID oid = new OID(bs);
        checkState(!oid.isAnchor());
        return oid;
    }

    /**
     * Translate a store SID to the original OID of the folder it was converted from.
     */
    public static OID convertedStoreSID2folderOID(SID sid)
    {
        checkArgument(!sid.isUserRoot(), sid);
        byte[] bs = Arrays.copyOf(sid.getBytes(), UniqueID.LENGTH);
        setVersionNibble(bs, 4);
        OID oid = new OID(bs);
        checkState(!oid.isAnchor());
        return oid;
    }

    /**
     * Translate a store SID to the OID of the anchor that holds the store
     */
    public static OID storeSID2anchorOID(SID sid)
    {
        checkArgument(!sid.isUserRoot(), sid);
        OID oid = new OID(sid.getBytes());
        checkState(oid.isAnchor());
        return oid;
    }

    /**
     * Translate the OID of an anchor to the SID of the store that the anchor
     * holds
     */
    public static SID anchorOID2storeSID(OID oid)
    {
        checkArgument(oid.isAnchor(), oid);
        SID sid = new SID(oid.getBytes());
        checkState(!sid.isUserRoot());
        return sid;
    }

    /**
     * Translate a user ID to the SID of the user's root store
     */
    public static SID rootSID(UserID userId)
    {
        MessageDigest md = newMessageDigestMD5();
        md.update(userId.getString().getBytes(StandardCharsets.UTF_8));
        byte[] bs = md.digest(ROOT_SID_SALT);
        setVersionNibble(bs, 3);
        SID sid = new SID(bs);
        checkState(sid.isUserRoot());
        return sid;
    }

    @Override
    public String toString()
    {
        return toStringImpl('$', 2, '$');
    }

    public static SID fromStringFormal(String hex) throws ExInvalidID
    {
        return new SID(UniqueID.fromStringFormal(hex));
    }
}
