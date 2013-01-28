package com.aerofs.daemon.mobile;

import java.io.IOException;
import java.io.InputStream;

import com.aerofs.daemon.core.acl.ACLChecker;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import com.google.inject.Inject;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;

public class HdDownloadPacket extends AbstractHdIMC<EIDownloadPacket>
{
    private final ACLChecker _acl;
    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;

    @Inject
    public HdDownloadPacket(ACLChecker acl,
            DirectoryService ds,
            NativeVersionControl nvc)
    {
        _acl = acl;
        _ds = ds;
        _nvc = nvc;
    }

    @Override
    protected void handleThrows_(EIDownloadPacket ev, Prio prio) throws Exception
    {
        SOID soid = _acl.checkThrows_(ev.user(), ev._path, Role.VIEWER);
        SOCKID sockid = new SOCKID(soid, CID.CONTENT, KIndex.MASTER);

        Version vLocal = _nvc.getLocalVersion_(sockid);
        ev._localVersion = vLocal;

        OA oa = _ds.getOAThrows_(soid);
        CA ca = oa.caMasterThrows();
        IPhysicalFile pf = ca.physicalFile();
        ev._fileLength = pf.getLength_();
        ev._fileModTime = pf.getLastModificationOrCurrentTime_();

        if (ev._inFileLength != -1 && ev._inFileLength != ev._fileLength) {
            throw mismatch();
        }

        if (ev._inFileLength != EIDownloadPacket.UNDEFINED_LENGTH &&
                ev._inFileModTime != EIDownloadPacket.UNDEFINED_MOD_TIME &&
                pf.wasModifiedSince(ev._inFileModTime, ev._inFileLength)) {
            throw mismatch();
        }

        if (ev._packetSize > 0) {
            byte[] buffer = new byte[ev._packetSize];
            ChannelBuffer cb = ev._data = ChannelBuffers.wrappedBuffer(buffer);
            cb.clear();
            InputStream in = pf.newInputStream_();
            try {
                if (ev._offset != 0) {
                    long skipped = in.skip(ev._offset);
                    if (skipped != ev._offset) throw new IOException("Error skipping to offset");
                }
                cb.writeBytes(in, ev._packetSize);
            } finally {
                in.close();
            }
        }

        ev._done = (ev._fileLength == 0) || (ev._offset + ev._packetSize == ev._fileLength);
    }

    private Exception mismatch()
    {
        return new ExStreamInvalid(InvalidationReason.UPDATE_IN_PROGRESS);
    }

}
