/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.manage;

import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.gui.GUIUtil;
import org.eclipse.jface.viewers.ColumnLabelProvider;

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
            // display a downward arrow if we have a context menu for this member
            return _ul.hasContextMenu((SubjectRolePair)elem) ? "" + GUIUtil.TRIANGLE_DOWNWARD : "";
        } else {
            return "";
        }
    }
}
