package com.aerofs.gui.sharing.folders;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Path;
import org.eclipse.swt.graphics.Image;

import com.aerofs.gui.Images;
import org.slf4j.Logger;

public class LabelProvider extends org.eclipse.jface.viewers.LabelProvider
{
    protected static Logger l = Loggers.getLogger(LabelProvider.class);
    private ContentProvider _cp;

    LabelProvider(ContentProvider cp)
    {
        _cp = cp;
    }

    @Override
    public Image getImage(Object element)
    {
        if (element instanceof Exception) {
            return Images.get(Images.ICON_ERROR);
        } else {
            if (element instanceof Path) {
                Path path = (Path) element;
                if(_cp.isPathForSharedFolder(path)) {
                    return Images.getSharedFolderIcon();
                }
            } else {
                // In case we don't see an object of type Path, we will display the Folder icon but
                // will log that error here.
                l.warn("Unexpected type of object {}", element.getClass());
            }
            return Images.getFolderIcon();
        }
    }

    @Override
    public String getText(Object element)
    {
        if (element instanceof Path) {
            return ((Path) element).last();
        } else {
            return super.getText(element);
        }
    }

}
