package com.aerofs.gui;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class SimpleContentProvider implements IStructuredContentProvider
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
     * @param input an array of entries or AeroException
     */
    @Override
    public Object[] getElements(Object input)
    {
        return (Object[]) input;
    }
}
