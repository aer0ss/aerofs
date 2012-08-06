package com.aerofs.gui.transfers;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;


import com.aerofs.gui.TransferState;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;

public class ContentProvider implements IStructuredContentProvider
{
    @Override
    public void dispose()
    {
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
    {
    }

    /**
     * @param input a TransferState, an AeroException, or a String
     */
    @Override
    public Object[] getElements(Object input)
    {
        if (!(input instanceof TransferState)) return new Object[] { input };

        TransferState ts = (TransferState) input;

        synchronized (ts) {
            Object ret[] = new Object[ts.downloads_().size() + ts.uploads_().size()];
            int idx = 0;

            for (PBDownloadEvent ev : ts.downloads_().values()) ret[idx++] = ev;
            for (PBUploadEvent ev : ts.uploads_().values()) ret[idx++] = ev;

            assert ret.length == idx;
            return ret;
        }
    }
}
