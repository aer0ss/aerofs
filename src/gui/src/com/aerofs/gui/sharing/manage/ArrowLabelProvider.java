package com.aerofs.gui.sharing.manage;

import org.eclipse.jface.viewers.ColumnLabelProvider;

import com.aerofs.lib.SubjectRolePair;

class ArrowLabelProvider extends ColumnLabelProvider
{
    private final CompUserList _ul;

    ArrowLabelProvider(CompUserList ul)
    {
        _ul = ul;
    }

    @Override
    public String getText(Object elem)
    {
        if (elem instanceof SubjectRolePair) {
            // display a downward arrow if the user can change the role
            return _ul.canChangeACL((SubjectRolePair) elem) ? "\u25BE" : "";
        } else {
            return "";
        }
    }
}
