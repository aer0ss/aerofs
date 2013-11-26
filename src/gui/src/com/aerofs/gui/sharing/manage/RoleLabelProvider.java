package com.aerofs.gui.sharing.manage;

class RoleLabelProvider extends SharingLabelProvider
{
    @Override
    public String getText(Object elem)
    {
        if (elem instanceof SharedFolderMember) {
            return ((SharedFolderMember)elem)._permissions.roleName();
        } else {
            return "";
        }
    }
}
