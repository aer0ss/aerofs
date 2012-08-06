package com.aerofs.daemon.core.net.proto;

import java.util.ArrayList;
import java.util.Collection;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IMapSIndex2Store;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBListRevChildrenRequest;
import com.aerofs.proto.Core.PBListRevChildrenResponse;
import com.google.inject.Inject;

public class ListRevChildren
extends AbstractListRevChildrenHistory<IListRevChildrenListener>
{
    private final IPhysicalStorage _ps;

    @Inject
    public ListRevChildren(IMapSIndex2Store sidx2s, DirectoryService ds, NSL nsl, IPhysicalStorage ps)
    {
        super(nsl, ds, sidx2s);
        _ps = ps;
    }

    @Override
    protected PBCore.Builder newRequest_(Path path)
    {
        Util.l(this).warn(">>> convert into relative path");
        return CoreUtil.newCore(Type.LIST_REV_CHILDREN_REQUEST)
            .setListRevChildrenRequest(PBListRevChildrenRequest.newBuilder()
                .setSeq(getSeq_(path))
                .addAllObjectPathElement(path.asList()));
    }

    @Override
    public void processRequestImpl_(DigestedMessage msg) throws Exception
    {
        if (!msg.pb().hasListRevChildrenRequest()) {
            throw new ExProtocolError(PBListRevChildrenRequest.class);
        }

        PBListRevChildrenRequest req = msg.pb().getListRevChildrenRequest();

        PBListRevChildrenResponse.Builder bd = PBListRevChildrenResponse
                .newBuilder()
                .setSeq(req.getSeq());

        Path path = new Path(req.getObjectPathElementList());
        for (Child child : _ps.getRevProvider().listRevChildren_(path)) {
            bd.addName(child._name).addDir(child._dir);
        }

        PBCore core = CoreUtil.newCore(Type.LIST_REV_CHILDREN_RESPONSE)
            .setListRevChildrenResponse(bd)
            .build();

        _nsl.sendUnicast_(msg.did(), msg.sidx(), core);
    }

    private void received_(RCHListeners ls, DID did, Collection<Child> children)
    {
        try {
            for (IListRevChildrenListener l : ls.beginIterating_()) {
                l.received_(did, children);
            }
        } finally {
            ls.endIterating_();
        }
    }

    @Override
    public void processResponseImpl_(DigestedMessage msg) throws ExProtocolError
    {
        if (!msg.pb().hasListRevChildrenResponse()) {
            throw new ExProtocolError(PBListRevChildrenResponse.class);
        }

        PBListRevChildrenResponse resp = msg.pb().getListRevChildrenResponse();
        if (resp.getNameCount() != resp.getDirCount()) {
            throw new ExProtocolError("count mismatch");
        }

        Path spath = getPath_(resp.getSeq());
        if (spath == null) return;

        ArrayList<Child> children = new ArrayList<Child>(resp.getNameCount());
        for (int i = 0; i < resp.getNameCount(); i++) {
            children.add(new Child(resp.getName(i), resp.getDir(i)));
        }

        RCHListeners ls = getListeners_(spath);
        received_(ls, msg.did(), children);
    }

    @Override
    protected void fetchFromLocal_(RCHListeners ls, Path spath)
            throws Exception
    {
        Util.l(this).warn(">>> convert into relative path");
        received_(ls, Cfg.did(), _ps.getRevProvider().listRevChildren_(spath));
    }
}
