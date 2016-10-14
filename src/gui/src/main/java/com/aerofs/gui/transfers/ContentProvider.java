package com.aerofs.gui.transfers;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.aerofs.gui.TransferState;

public class ContentProvider implements IStructuredContentProvider
{
    @Override
    public void dispose()
    {
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
    {
        viewer.refresh();
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
            return ts.transfers_().toArray();
        }
    }
}
