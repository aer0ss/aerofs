package com.aerofs.daemon.fsi;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.*;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.Path;

// this object emulates the interface of java.io.File
public class FSIFile {

    public static final Prio PRIO = Prio.HI;

    private final Path _path;
    private final IIMCExecutor _imce;
    private final String _user;

    /**
     * @param user null to use local user name (Cfg.user())
     */
    public FSIFile(String user, IIMCExecutor imce, String ... path)
    {
        this(user, new Path(path), imce);
    }

    /**
     * @param user null to use local user name (Cfg.user())
     */
    public FSIFile(String user, Path path, IIMCExecutor imce)
    {
        _user = user == null ? Cfg.user() : user;
        _imce = imce;
        _path = path;
    }

    /**
     * @param user null to use local user name (Cfg.user())
     */
    public FSIFile(String user, Path path)
    {
        this(user, path, Core.imce());
    }

    // used by JNI
    public FSIFile(String[] path)
    {
        this(null, new Path(path), Core.imce());
    }

    /**
     * create the object and set the oid and attributes of 'this'
     * @return false if the object already exists.
     */
    boolean createObject(boolean dir) throws Exception
    {
        EICreateObject ev = new EICreateObject(_user, _imce, getPath(), dir);
        ev.execute(PRIO);
        return !ev._exist;
    }

    public Path getPath()
    {
        return _path;
    }

    IIMCExecutor imce()
    {
        return _imce;
    }

    String user()
    {
        return _user;
    }

    // native exceptions:
    //      ExAlreadyExist: path already exists
    //      native exceptions from createObject_()
    public void mkdir(boolean exclusive) throws Exception
    {
        if (!createObject(true) && exclusive) throw new ExAlreadyExist();
    }

    // native exceptions:
    //      same as mkdir()
    public void createNewFile() throws Exception
    {
        if (!createObject(false)) throw new ExAlreadyExist();
    }

    public boolean exists() throws Exception
    {
        return getAttr() != null;
    }

    public OA getAttrThrows() throws Exception
    {
        OA oa = getAttr();
        if (oa == null) throw new ExNotFound();
        return oa;
    }

    /**
     * @return null if not found
     */
    public OA getAttr() throws Exception
    {
        OA oa;
//        SOID soid = The.core().ds().resolveCached(getPath());
//        if (soid != null) {
//            oa = The.core().ds().getCachedOA(soid);
//        } else {
//            oa = null;
//        }
//
//        if (soid == null || oa == null) {
            EIGetAttr ev = new EIGetAttr(_user, _imce, getPath());
            ev.execute(PRIO);
            oa = ev._oa;
//        }

        return oa;
    }

    public void move(Path parent, String name) throws Exception
    {
        Path from = getPath();

        new EIMoveObject(_user, from, parent, name, _imce).execute(PRIO);
    }


    // setting any field to null avoids it to be modified
    public void setAttr(Integer flags)
        throws Exception
    {
        new EISetAttr(_user, _imce, _path, flags).execute(PRIO);
    }

    public void setFlags(int flags) throws Exception
    {
        setAttr(flags);
    }

    public void delete() throws Exception
    {
        EIDeleteObject ev = new EIDeleteObject(_user, _imce, getPath());
        ev.execute(PRIO);
    }

    public void deleteBranch(KIndex kidx) throws Exception
    {
        EIDeleteBranch ev = new EIDeleteBranch(_user, _imce, getPath(), kidx);
        ev.execute(PRIO);
    }
}
