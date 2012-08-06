package com.aerofs.gui.exclusion;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class ContentProvider implements ITreeContentProvider
{
    @Override
    public Object[] getElements(Object arg0)
    {
        return (Object[]) arg0;
    }

    @Override
    public void dispose()
    {
    }

    @Override
    public void inputChanged(Viewer arg0, Object arg1, Object arg2)
    {
    }

    @Override
    public Object[] getChildren(Object arg0)
    {
        return null;
    }

    @Override
    public Object getParent(Object arg0)
    {
        return null;
    }

    @Override
    public boolean hasChildren(Object arg0)
    {
        return false;
    }

}
