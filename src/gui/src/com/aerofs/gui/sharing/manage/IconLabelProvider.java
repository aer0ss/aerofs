package com.aerofs.gui.sharing.manage;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Image;

import com.aerofs.gui.Images;
import com.aerofs.lib.acl.SubjectRolePair;

class IconLabelProvider extends ColumnLabelProvider
{
     @Override
    public Image getImage(Object elem)
    {
        if (elem instanceof Exception) return Images.get(Images.ICON_ERROR);
        else if (elem instanceof String) return null;

        SubjectRolePair srp = (SubjectRolePair) elem;
        switch (srp._role) {
        case OWNER:
            return Images.get(Images.ICON_USER_KEY);
        case EDITOR:
            return Images.get(Images.ICON_PEN);
        default:
            assert false;
            return null;
        }
    }

     @Override
     public String getText(Object elem)
     {
         return "";
     }
}
