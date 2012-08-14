
package com.aerofs.daemon.core.ds;

import java.util.SortedMap;

import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;

/**
 * Object Attribute
 */
public class OA
{
    public static enum Type {
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
    private final FID _fid;
    private IPhysicalFolder _pf;
    private int _flags;

    // Sorted map of KIndices and corresponding CAs.
    // Useful for iterating over KIndices in sorted order.
    private final SortedMap<KIndex, CA> _cas;

    // use "R" instead of an empty string as Path mandates non-empty path elements
    public static final String ROOT_DIR_NAME = "R";

    /**
     * @param cas list of content attributes. null iff the object is a
     * directory or an anchor
     */
    public OA(SOID soid, OID parent, String name, Type type, SortedMap<KIndex, CA> cas, int flags,
            FID fid)
    {
        assert (type == Type.FILE) == (cas != null);

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
        return "s " + _soid + " p " + _parent + " n " + (Cfg.staging() ? _name : Util.crc32(_name))
                + " cas " + _cas;
    }

    /**
     * @return the attribute of the master branch, null if not present
     */
    public CA caMaster()
    {
        assert isFile();
        return ca(KIndex.MASTER);
    }

    public CA caMasterThrows() throws ExNotFound
    {
        CA ca = caMaster();
        if (ca == null) throw new ExNotFound(_soid + " master branch");
        return ca;
    }

    /**
     * @return the attribute of the master branch, null if not present
     */
    public CA ca(KIndex kidx)
    {
        assert isFile();
        return _cas.get(kidx);
    }

    public CA caThrows(KIndex kidx) throws ExNotFound
    {
        CA ca = ca(kidx);
        if (ca == null) throw new ExNotFound(_soid + " branch " + kidx);
        return ca;
    }

    /**
     * @return Returns sorted map of KIndices and corresponding CAs.
     *         Useful for iterating over KIndices in sorted order.
     *         Empty if no branch is present.
     */
    public SortedMap<KIndex, CA> cas(boolean assertConsistency)
    {
        assert isFile();
        if (assertConsistency) assert !isExpelled() || _cas.isEmpty() : _soid + " " + _cas.size();
        return _cas;
    }

    public SortedMap<KIndex, CA> cas()
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
     * @return may be null if it's not a linked file or it's not present
     */
    public FID fid()
    {
        if (isFile()) {
            assert (_fid == null) == cas().isEmpty() : soid() + " " + _fid + " " + cas();
        } else {
            assert (_fid == null) == isExpelled() : soid() + " " + _fid + " " + isExpelled();
        }

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
