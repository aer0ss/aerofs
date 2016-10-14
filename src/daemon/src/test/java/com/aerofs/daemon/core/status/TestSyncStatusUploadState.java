package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSyncStatusUploadState extends AbstractSyncStatusTest
{
    @Test
    public void shouldAddAndRemoveTSDevicesAndIgnoreOthers() {
        syncStatusUploadState.onTransferStateChanged_(
                new TransferredItem(new SOCID(rootSIndex, moria.oid(), CID.CONTENT),
                        new Endpoint(null, storageAgentDID1)),
                new TransferProgress(0, 100));
        assertTrue(syncStatusUploadState.contains(moria));
        syncStatusUploadState.onTransferStateChanged_(
                new TransferredItem(new SOCID(rootSIndex, moria.oid(), CID.CONTENT),
                        new Endpoint(null, storageAgentDID2)),
                new TransferProgress(0, 100));
        assertTrue(syncStatusUploadState.contains(moria));
        syncStatusUploadState.onTransferStateChanged_(
                new TransferredItem(new SOCID(rootSIndex, moria.oid(), CID.CONTENT),
                        new Endpoint(null, storageAgentDID1)),
                new TransferProgress(100, 100));
        assertFalse(syncStatusUploadState.contains(moria));
        syncStatusUploadState.onTransferStateChanged_(
                new TransferredItem(new SOCID(rootSIndex, moria.oid(), CID.CONTENT),
                        new Endpoint(null, storageAgentDID2)),
                new TransferProgress(100, 100));
        assertFalse(syncStatusUploadState.contains(moria));
        syncStatusUploadState.onTransferStateChanged_(
                new TransferredItem(new SOCID(rootSIndex, moria.oid(), CID.CONTENT),
                        new Endpoint(null, irrelevant)),
                new TransferProgress(0, 100));
        assertFalse(syncStatusUploadState.contains(moria));
    }
}
