package com.aerofs.daemon.core.ds;

import java.util.SortedMap;

import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableSortedMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Object Attribute
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

    // the expelled bit inherited from the parent
    public static final int FLAG_EXPELLED_INH = 0x0001;
    // the expelled bit set by the user
    public static final int FLAG_EXPELLED_ORG = 0x0002;

    public static final int FLAG_EXPELLED_ORG_OR_INH = FLAG_EXPELLED_ORG | FLAG_EXPELLED_INH;

    // these flags are not be synced across devices
    public static final int FLAGS_LOCAL = FLAG_EXPELLED_ORG_OR_INH;

    /* We currently don't support OS-specific flags
    // see "attrib /?" for these windows-specific flags
    public static final int FLAG_OS_WIN_HIDDEN      = 0x0010;
    public static final int FLAG_OS_WIN_SYSTEM      = 0x0020;
    public static final int FLAG_OS_WIN_READONLY    = 0x0040;
    public static final int FLAG_OS_WIN_ARCHIVE     = 0x0080;
    public static final int FLAG_OS_WIN_NOINDEX     = 0x0100;
    */

    private final SOID _soid;
    private final OID _parent;
    private final String _name;
    private final Type _type;
    @Nullable private final FID _fid;
    private IPhysicalFolder _pf;
    private int _flags;

    // Sorted map of KIndices and corresponding CAs.
    // Useful for iterating over KIndices in sorted order.
    @Nullable private final ImmutableSortedMap<KIndex, CA> _cas;

    // use "R" instead of an empty string as Path mandates non-empty path elements
    public static final String ROOT_DIR_NAME = "R";

    @Nonnull public static OA createFile(SOID soid, OID parent, String name,
            SortedMap<KIndex, CA> cas, int flags, @Nullable FID fid)
    {
        return new OA(soid, parent, name, Type.FILE, ImmutableSortedMap.copyOfSorted(cas),
                flags, fid);
    }

    @Nonnull public static OA createNonFile(SOID soid, OID parent, String name, Type type,
            int flags, @Nullable FID fid)
    {
        assert !type.equals(Type.FILE) : type + " " + soid;
        return new OA(soid, parent, name, type, null, flags, fid);
    }

    /**
     * @param cas map of content attributes. null iff the object is a
     * directory or an anchor
     */
    private OA(SOID soid, OID parent, String name, Type type,
            @Nullable ImmutableSortedMap<KIndex, CA> cas, int flags, @Nullable FID fid)
    {
        assert soid.oid().isRoot() || !soid.oid().equals(parent) : parent;

        _soid = soid;
        _parent = parent;
        _name = name;
        _type = type;
        _cas = cas;
        _flags = flags;
        _fid = fid;

        // TODO Can't assert validity of FIDs at construction time for files or folders
        //fidIsConsistentWithCAsOrExpulsion();
    }

    @Override
    public String toString()
    {
        return "s " + _soid + " p " + _parent + " n " + (Cfg.staging() ? _name : Util.crc32(_name))
                + " f " + String.format("%08X", _flags) + " fid " + _fid + " cas " + _cas;
    }

    /**
     * @return the attribute of the master branch, null if not present
     */
    @Nullable public CA caMasterNullable()
    {
        assert isFile();
        return caNullable(KIndex.MASTER);
    }

    @Nonnull public CA caMasterThrows() throws ExNotFound
    {
        CA ca = caMasterNullable();
        if (ca == null) throw new ExNotFound(_soid + " master branch");
        return ca;
    }

    @Nonnull public CA caMaster()
    {
        CA ca = caMasterNullable();
        assert ca != null;
        return ca;
    }

    /**
     * @return the attribute of the branch, null if not present
     */
    @Nullable public CA caNullable(KIndex kidx)
    {
        assert isFile();
        assert _cas != null : this;
        return _cas.get(kidx);
    }

    @Nonnull public CA caThrows(KIndex kidx) throws ExNotFound
    {
        CA ca = caNullable(kidx);
        if (ca == null) throw new ExNotFound(_soid + " branch " + kidx);
        return ca;
    }

    @Nonnull public CA ca(KIndex kidx)
    {
        CA ca = caNullable(kidx);
        assert ca != null : this + " " + kidx;
        return ca;
    }

    /**
     * @return Returns sorted map of KIndices and corresponding CAs.
     *         Useful for iterating over KIndices in sorted order.
     *         Empty if no branch is present.
     */
    @Nonnull public SortedMap<KIndex, CA> cas(boolean assertConsistency)
    {
        assert isFile();
        assert _cas != null : this;
        if (assertConsistency) assert !isExpelled() || _cas.isEmpty() : _soid + " " + _cas.size();
        return _cas;
    }

    @Nonnull public SortedMap<KIndex, CA> cas()
    {
        return cas(true);
    }

    public Type type()
    {
        return _type;
    }

    public boolean isDir()
    {
        return _type == Type.DIR;
    }

    public boolean isDirOrAnchor()
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
     * @return one or more FLAG_* values
     */
    public int flags()
    {
        return _flags;
    }

    public boolean isExpelled()
    {
        return Util.test(_flags, FLAG_EXPELLED_ORG_OR_INH);
    }

    public void flags(int flags)
    {
        _flags = flags;
    }

    /**
     * @return true iff the nullability of _fid is consistent with the set of content attributes
     *         or the expulsion state (if this is a folder)
     */
    public boolean fidIsConsistentWithCAsOrExpulsion()
    {
        if (isFile()) {
            return (_fid == null) == cas().isEmpty();
        } else {
            return (_fid == null) == isExpelled();
        }
    }

    /**
     * @return may be null if it's not a linked file or it's not present
     */
    @Nullable public FID fid()
    {
        assert fidIsConsistentWithCAsOrExpulsion() : this;
        return _fid;
    }

    /**
     * internal use. only DirectoryService should call this.
     */
    void setPhyFolder_(IPhysicalFolder pf)
    {
        assert !isFile() && _pf == null;
        _pf = pf;
    }

    /**
     * only folders and anchors have phyical folders
     */
    public IPhysicalFolder physicalFolder()
    {
        assert !isFile();
        return _pf;
    }
}
