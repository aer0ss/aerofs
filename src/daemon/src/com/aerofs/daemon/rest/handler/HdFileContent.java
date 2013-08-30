package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;

public class HdFileContent extends AbstractHdIMC<EIFileContent>
{
    private final ACLChecker _acl;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdFileContent(DirectoryService ds, ACLChecker acl, IMapSID2SIndex sid2sidx)
    {
        _ds = ds;
        _acl = acl;
        _sid2sidx = sid2sidx;
    }

    @Override
    protected void handleThrows_(EIFileContent ev, Prio prio) throws Exception
    {
        SID sid = ev._object.sid;
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExNotFound();

        _acl.checkThrows_(ev._user, sidx, Role.VIEWER);

        SOID soid = new SOID(sidx, ev._object.oid);
        OA oa = _ds.getOAThrows_(soid);

        if (!oa.isFile()) throw new ExNotFile();
        if (oa.isExpelled()) throw new ExExpelled();

        CA ca = oa.caMasterThrows();
        final IPhysicalFile pf = ca.physicalFile();

        ev.setResult_(new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream)
                    throws IOException, WebApplicationException
            {
                // TODO: manual chunking to check for changes during upload
                // ideally code should be shared w/ GCCSendContent
                ByteStreams.copy(pf.newInputStream_(), outputStream);
            }
        });
    }
}
