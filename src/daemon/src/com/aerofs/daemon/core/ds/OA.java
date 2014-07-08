package com.aerofs.daemon.core.ds;

import java.util.SortedMap;

import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableSortedMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Object Attribute
 *
 * NB: make sure not to use stale OAs (e.g. after performing a move, an expulsion, or
 * releasing the core lock).
 *
 * Do NOT cache OAs. DirectoryService maintains a cache and will invalidate it correctly
 * when changes are made
 */
public class OA
{
    public static enum Type {
        // N.B. Only append to this list.  If you change ordinals,
        // you will change the meaning of existing DBs.
        // That would be bad.
        FILE,
        DIR,
        ANCHOR;

        public static Type valueOf(int i)
        {
            return Type.values()[i];
        }
    }

    // the expelled bit set by the user
    public static final int FLAG_EXPELLED_ORG = 0x0001;

    // OR combination of flags that should be stored in the DB
    public static final int FLAG_DB_MASK =
            FLAG_EXPELLED_ORG;

    // internal flag, not stored in the DB but set by DS instead
    static final int FLAG_EXPELLED_INH = 0x40000000;

    // internal flag, if absent, the above flags that
    // are DS-provided cannot be relied on
    static final int FLAG_DS_VALIDATED = 0x80000000;

    private final SOID _soid;
    private final OID _parent;
    private final String _name;
    private final Type _type;
    @Nullable private final FID _fid;
    private int _flags;

    // Sorted map of KIndices and corresponding CAs.
    // Useful for iterating over KIndices in sorted order.
    @Nullable private final ImmutableSortedMap<KIndex, CA> _cas;

    // use an empty string to avoid conflicts with existing files
    // NB: Path does not allow empty components but this is not an issue because root dirs are just
    // an artifact of the store hierarchy and should never ever appear in a Path...
    public static final String ROOT_DIR_NAME = "";

    @Nonnull public static OA createFile(SOID soid, OID parent, String name,
            SortedMap<KIndex, CA> cas, int flags, @Nullable FID fid)
    {
        return new OA(soid, parent, name, Type.FILE, ImmutableSortedMap.copyOfSorted(cas),
                flags, fid);
    }

    @Nonnull public static OA createNonFile(SOID soid, OID parent, String name, Type type,
            int flags, @Nullable FID fid)
    {
        checkArgument(!type.equals(Type.FILE), "%s %s", type, soid);
        return new OA(soid, parent, name, type, null, flags, fid);
    }

    /**
     * @param cas map of content attributes. null iff the object is a
     * directory or an anchor
     */
    private OA(SOID soid, OID parent, String name, Type type,
            @Nullable ImmutableSortedMap<KIndex, CA> cas, int flags, @Nullable FID fid)
    {
        checkArgument(soid.oid().isRoot() || !soid.oid().equals(parent), parent);

        _soid = soid;
        _parent = parent;
        _name = name;
        _type = type;
        _cas = cas;
        _flags = flags;
        _fid = fid;
    }

    @Override
    public String toString()
    {
        return "s " + _soid + " p " + _parent + " n " + Util.crc32(_name)
                + " f " + String.format("%08X", _flags) + " fid " + _fid + " cas " + _cas;
    }

    /**
     * @return the attribute of the master branch, null if not present
     */
    @Nullable public final CA caMasterNullable()
    {
        return caNullable(KIndex.MASTER);
    }

    @Nonnull public final CA caMasterThrows() throws ExNotFound
    {
        CA ca = caMasterNullable();
        if (ca == null) throw new ExNotFound(_soid + " master branch");
        return ca;
    }

    @Nonnull public final CA caMaster()
    {
        CA ca = caMasterNullable();
        return checkNotNull(ca);
    }

    /**
     * @return the attribute of the branch, null if not present
     */
    @Nullable public final CA caNullable(KIndex kidx)
    {
        checkArgument(!isExpelled());
        return casNoExpulsionCheck().get(kidx);
    }

    @Nonnull public final CA caThrows(KIndex kidx) throws ExNotFound
    {
        CA ca = caNullable(kidx);
        if (ca == null) throw new ExNotFound(_soid + " branch " + kidx);
        return ca;
    }

    @Nonnull public final CA ca(KIndex kidx)
    {
        CA ca = caNullable(kidx);
        return checkNotNull(ca, "%s %s", this, kidx);
    }

    /**
     * @return Returns sorted map of KIndices and corresponding CAs.
     *         Useful for iterating over KIndices in sorted order.
     *         Empty if no branch is present.
     */
    @Nonnull public final SortedMap<KIndex, CA> cas()
    {
        return isExpelled() ? ImmutableSortedMap.<KIndex, CA>of() : casNoExpulsionCheck();
    }

    /**
     * DO NOT USE outside of LogicalStagingArea
     *
     * To preserve externally observable behavior when doing incremental cleanup of expelled
     * logical trees, by default we filter out CAs for expelled files. In the logical staging
     * area we need to bypass that filtering, hence this method.
     */
    @Nonnull public SortedMap<KIndex, CA> casNoExpulsionCheck()
    {
        checkArgument(isFile());
        return checkNotNull(_cas, this);
    }

    public Type type()
    {
        return _type;
    }

    public boolean isDir()
    {
        return _type == Type.DIR;
    }

    public final boolean isDirOrAnchor()
    {
        return isDir() || isAnchor();
    }

    public boolean isFile()
    {
        return _type == Type.FILE;
    }

    public boolean isAnchor()
    {
        return _type == Type.ANCHOR;
    }

    public String name()
    {
        return _name;
    }

    public OID parent()
    {
        return _parent;
    }

    public SOID soid()
    {
        return _soid;
    }

    /**
     * Whether the object or any of its ancestor is expelled
     *
     * WARNING: can only be used for objects obtained from DirectoryService
     * Will throw if the object was obtained directly from IMetaDatabase
     */
    public boolean isExpelled()
    {
        checkState(Util.test(_flags, FLAG_DS_VALIDATED));
        return Util.test(_flags, FLAG_EXPELLED_INH | FLAG_EXPELLED_ORG);
    }

    /**
     * Whether the object itself is expelled
     */
    public boolean isSelfExpelled()
    {
        return Util.test(_flags, FLAG_EXPELLED_ORG);
    }

    /**
     * @return one or more FLAG_* values
     *
     * NB: only public for DPUT use
     */
    public int flags()
    {
        return _flags;
    }

    void setFlags(int flags)
    {
        _flags = flags;
    }

    /**
     * @return may be null if it's not a linked file or it's not present
     */
    @Nullable public FID fid()
    {
        // incremental expulsion must preserve externally observable behavior
        // so we always return a null FID for expelled objects, even though
        // the DB may still contain a stale FID
        return isExpelled() ? null : _fid;
    }

    /**
     * DO NOT USE outside of LogicalStagingArea
     *
     * To preserve externally observable behavior when doing incremental cleanup of expelled
     * logical trees, by default we filter out FID for expelled objects. In the logical staging
     * area we need to bypass that filtering, hence this method.
     */
    @Nullable public FID fidNoExpulsionCheck()
    {
        return _fid;
    }
}
