/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.users;

import com.aerofs.gui.GUIUtil;

class ArrowLabelProvider extends SharingLabelProvider
{
    @Override
    public String getText(Object elem)
    {
        return elem instanceof SharedFolderMember
                && RoleMenu.hasContextMenu((SharedFolderMember)elem) ?
                GUIUtil.TRIANGLE_DOWNWARD :
                "";
    }
}
