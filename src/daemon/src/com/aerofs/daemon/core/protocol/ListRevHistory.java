package com.aerofs.daemon.core.protocol;

import java.util.ArrayList;
import java.util.Collection;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBListRevHistoryRequest;
import com.aerofs.proto.Core.PBListRevHistoryResponse;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

public class ListRevHistory
extends AbstractListRevChildrenHistory<IListRevHistoryListener>
{
    private final IPhysicalStorage _ps;
    private final CfgLocalDID _cfgLocalDID;

    @Inject
    public ListRevHistory(MapSIndex2Store sidx2s, DirectoryService ds, NSL nsl, IPhysicalStorage ps,
            CfgLocalDID cfgLocalDID)
    {
        super(nsl, ds, sidx2s);
        _ps = ps;
        _cfgLocalDID = cfgLocalDID;
    }

    @Override
    protected PBCore.Builder newRequest_(Path path)
    {
        Util.l(this).warn(">>>> the path may not be absolute path?");
        return CoreUtil.newCore(Type.LIST_REV_HISTORY_REQUEST)
            .setListRevHistoryRequest(PBListRevHistoryRequest.newBuilder()
                .setSeq(getSeq_(path))
                .addAllObjectPathElement(path.asList()));
    }

    @Override
    public void processRequestImpl_(DigestedMessage msg) throws Exception
    {
        if (!msg.pb().hasListRevHistoryRequest()) {
            throw new ExProtocolError(PBListRevHistoryRequest.class);
        }

        PBListRevHistoryRequest req = msg.pb().getListRevHistoryRequest();

        PBListRevHistoryResponse.Builder bd = PBListRevHistoryResponse
                .newBuilder()
                .setSeq(req.getSeq());

        Path path = new Path(req.getObjectPathElementList());
        for (Revision en : _ps.getRevProvider().listRevHistory_(path)) {
            bd.addIndex(ByteString.copyFrom(en._index))
                .addVersion(Version.empty().toPB_())
                .addMtime(en._mtime)
                .addLength(en._length);
        }

        PBCore core = CoreUtil.newCore(Type.LIST_REV_HISTORY_RESPONSE)
            .setListRevHistoryResponse(bd)
            .build();

        _nsl.sendUnicast_(msg.did(), msg.sidx(), core);
    }

    private void received_(RCHListeners ls, DID did, Collection<Revision> ens)
    {
        try {
            for (IListRevHistoryListener l : ls.beginIterating_()) {
                l.received_(did, ens);
            }
        } finally {
            ls.endIterating_();
        }
    }

    @Override
    public void processResponseImpl_(DigestedMessage msg) throws ExProtocolError
    {
        if (!msg.pb().hasListRevHistoryResponse()) {
            throw new ExProtocolError(PBListRevHistoryResponse.class);
        }

        PBListRevHistoryResponse resp = msg.pb().getListRevHistoryResponse();
        if (resp.getVersionCount() != resp.getMtimeCount() ||
                resp.getMtimeCount() != resp.getLengthCount()) {
            throw new ExProtocolError("count mismatch");
        }

        Path spath = getPath_(resp.getSeq());
        if (spath == null) return;

        ArrayList<Revision> ens = new ArrayList<Revision>(
                resp.getVersionCount());
        for (int i = 0; i < resp.getVersionCount(); i++) {
            ens.add(new Revision(resp.getIndex(i).toByteArray(),
                    resp.getMtime(i), resp.getLength(i)));
        }

        RCHListeners ls = getListeners_(spath);
        received_(ls, msg.did(), ens);
    }

    @Override
    protected void fetchFromLocal_(RCHListeners ls, Path spath)
            throws Exception
    {
        Collection<Revision> revs = _ps.getRevProvider().listRevHistory_(spath);
        received_(ls, _cfgLocalDID.get(), revs);
    }
}
