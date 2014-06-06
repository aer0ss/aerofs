package com.aerofs.gui.exclusion;

import org.eclipse.swt.graphics.Image;

import com.aerofs.gui.Images;
import com.aerofs.lib.Path;

public class LabelProvider extends org.eclipse.jface.viewers.LabelProvider
{
    @Override
    public Image getImage(Object element)
    {
        if (element instanceof Exception) {
            return Images.get(Images.ICON_ERROR);
        } else {
            return Images.get(Images.ICON_FOLDER);
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
