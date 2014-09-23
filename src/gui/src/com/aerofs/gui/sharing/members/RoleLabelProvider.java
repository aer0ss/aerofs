package com.aerofs.gui.sharing.members;

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
