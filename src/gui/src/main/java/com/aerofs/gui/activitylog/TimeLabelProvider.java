package com.aerofs.gui.activitylog;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import com.aerofs.lib.Util;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;

class TimeLabelProvider extends ColumnLabelProvider
{
    @Override
    public String getText(Object element)
    {
        if (element instanceof PBActivity) {
            long time = ((PBActivity) element).getTime();
            return Util.formatAbsoluteTime(time);
        } else {
             return "";
        }
    }
}