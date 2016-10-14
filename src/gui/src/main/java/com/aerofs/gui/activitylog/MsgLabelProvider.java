package com.aerofs.gui.activitylog;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;

class MsgLabelProvider extends ColumnLabelProvider
{
    @Override
    public String getText(Object element)
    {
        if (element instanceof PBActivity) {
            return ((PBActivity) element).getMessage();
        } else {
            return element.toString();
        }
    }
}