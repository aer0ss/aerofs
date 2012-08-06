package com.aerofs.lib.fsi;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Fsi.PBFSICall.Type;
import com.aerofs.proto.Fsi.*;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class AeroFile
{
    static final Logger l = Util.l(AeroFile.class);

    private final FSIClient _fsi;
    private final PBPath _path;
    private final String _user;

    private PBObjectAttr _cOA;
    private Set<AeroFile> _cChildren;

    // set user to null to use daemon user
    public AeroFile(String user, FSIClient fsi, PBPath path, PBObjectAttr oa)
    {
        _fsi = fsi;
        _path = path;
        _user = user;
        _cOA = oa;
    }

    public AeroFile(String user, FSIClient fsi, PBPath path)
    {
        this (user, fsi, path, null);
    }

    public AeroFile(FSIClient fsi, PBPath path)
    {
        this(null, fsi, path);
    }

    // use f's user and fsi
    public AeroFile(AeroFile f, PBPath path)
    {
        this (f.getUser(), f.getFSI(), path, null);
    }

    public String getUser()
    {
        return _user;
    }

    public PBPath getPath()
    {
        return _path;
    }

    public String getName()
    {
        int size = _path.getElemCount();
        return size == 0 ? "" : _path.getElem(size - 1);
    }

    public FSIClient getFSI()
    {
        return _fsi;
    }

    PBFSICall.Builder newCallBuilderWithUser()
    {
        PBFSICall.Builder ret = PBFSICall.newBuilder();
        if (_user != null) ret.setUser(_user);
        return ret;
    }

    public void delete_() throws Exception
    {
        PBFSICall req = newCallBuilderWithUser()
            .setType(Type.DELETE_OBJECT)
            .setDeleteObject(
                PBDeleteObjectCall.newBuilder()
                .setPath(_path)
            ).build();

        _fsi.rpc_(req);

        invalidate_();
    }

    public void deleteBranch_(KIndex kidx) throws Exception
    {
        assert !kidx.equals(KIndex.MASTER);
        PBFSICall call = newCallBuilderWithUser()
            .setType(Type.DELETE_BRANCH)
            .setDeleteBranch(
                PBDeleteBranchCall.newBuilder()
                .setPath(_path)
                .setKidx(kidx.getInt())
            ).build();

        _fsi.rpc_(call);

        _cOA = null;
    }

    public void mkdir_() throws Exception
    {
        mkdir_(true);
    }

    public void mkdir_(boolean exclusive) throws Exception
    {
        PBFSICall req = newCallBuilderWithUser()
            .setType(Type.MKDIR)
            .setMkdir(
                PBMkdirCall.newBuilder()
                .setPath(_path)
                .setExclusive(exclusive)
            ).build();

        _fsi.rpc_(req);

        _cChildren = null;
    }

    public boolean isDir_() throws Exception
    {
        return getAttr_().getIsDir();
    }

    public boolean isAnchor_() throws Exception
    {
        return getAttr_().getIsAnchor();
    }

    // N.B. this object still refer to the old object after move
    public void move_(PBPath toParent, String toName)
        throws Exception
    {
        PBFSICall req = newCallBuilderWithUser()
            .setType(Type.MOVE_OBJECT)
            .setMoveObject(
                PBMoveObjectCall.newBuilder()
                .setFrom(_path)
                .setToParent(toParent)
                .setToName(toName)
            ).build();

        _fsi.rpc_(req);
    }

    // setting any field to null avoids it to be modified
    public void setAttr_(String owner, Boolean inheritable, Integer flags)
        throws Exception
    {
        PBSetAttrCall.Builder bdCall = PBSetAttrCall.newBuilder()
            .setPath(_path);

        if (owner != null) bdCall.setOwner(owner);
        if (inheritable != null) bdCall.setIsInheritable(inheritable);
        if (flags != null) bdCall.setFlags(flags);

        PBFSICall req = newCallBuilderWithUser()
            .setType(Type.SET_ATTR)
            .setSetAttr(bdCall)
            .build();
        _fsi.rpc_(req);

        _cOA = null;
    }

    // TODO: remove and use Ritual instead
    public PBObjectAttr getAttr_() throws Exception
    {
        if (_cOA == null) {
            PBGetAttrReply reply = _fsi.rpc_(newCallBuilderWithUser()
                    .setType(Type.GET_ATTR)
                    .setGetAttr(PBGetAttrCall.newBuilder()
                            .setPath(_path))).getGetAttr();
            _cOA = reply.getAttr();
        }

        return _cOA;
    }

    public Set<AeroFile> listChildren_()
        throws Exception
    {
        if (_cChildren == null) {
            PBFSICall req = newCallBuilderWithUser()
                .setType(Type.LIST_CHILDREN)
                .setListChildrenAttr(PBListChildrenAttrCall.newBuilder()
                    .setPath(_path))
                .build();

            Set<AeroFile> fs = new HashSet<AeroFile>();
            for (PBObjectAttr oa : _fsi.rpc_(req).getListChildrenAttr()
                    .getChildAttrList()) {
                fs.add(new AeroFile(_user, _fsi, FSIUtil.build(_path, oa.getName()), oa));
            }

            _cChildren = fs;
        }
        return _cChildren;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null) return false;

        return this == obj || ((obj instanceof AeroFile) &&
            FSIUtil.equals(_path, ((AeroFile) obj)._path));
    }

    @Override
    public int hashCode()
    {
        return FSIUtil.hashCode(_path);
    }

    @Override
    public String toString()
    {
        return getName();
    }

    /**
     * invalidate all cached state.
     */
    public void invalidate_()
    {
        _cOA = null;
        _cChildren = null;
    }

    public boolean exists_() throws Exception
    {
        // TODO create a fsi call to test existence instead of using exceptions
        try {
            getAttr_();
            return true;
        } catch (ExNotFound e) {
            return false;
        }
    }
}
