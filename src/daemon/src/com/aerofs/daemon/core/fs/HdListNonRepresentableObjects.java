/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.IPhysicalStorage.NonRepresentableObject;
import com.aerofs.daemon.event.fs.EIListNonRepresentableObjects;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.proto.Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.SQLException;

public class HdListNonRepresentableObjects extends AbstractHdIMC<EIListNonRepresentableObjects>
{
    private final IOSUtil _osutil;
    private final IPhysicalStorage _ps;
    private final DirectoryService _ds;

    @Inject
    public HdListNonRepresentableObjects(IOSUtil osutil, IPhysicalStorage ps, DirectoryService ds)
    {
        _ps = ps;
        _ds = ds;
        _osutil = osutil;
    }

    @Override
    protected void handleThrows_(EIListNonRepresentableObjects ev, Prio prio) throws Exception
    {
        ImmutableList.Builder<PBNonRepresentableObject> bd = ImmutableList.builder();

        for (NonRepresentableObject nro : _ps.listNonRepresentableObjects_()) {
            OA oa = _ds.getOANullable_(nro.soid);
            // NROs that are pending cleanup (i.e. in LogicalStagingArea) should be ignored
            if (oa == null || oa.isExpelled()) continue;
            ResolvedPath path = _ds.resolve_(oa);
            bd.add(PBNonRepresentableObject.newBuilder()
                    .setPath(path.toPB())
                    .setReason(reason(path, nro.conflict))
                    .build());
        }

        ev.setResult(bd.build());
    }

    private String reason(ResolvedPath path, @Nullable OID oid) throws SQLException
    {
        if (oid != null) {
            return "Conflicts with " + _ds.getOA_(new SOID(path.soid().sidx(), oid)).name();
        }

        String reason = _osutil.reasonForInvalidFilename(path.last());

        return reason != null ? reason : "Lack of permission or disk error";
    }
}
