package com.aerofs.gui.sharing.manage;

import org.apache.commons.lang.WordUtils;

class RoleLabelProvider extends SharingLabelProvider
{
    @Override
    public String getText(Object elem)
    {
        if (elem instanceof SharedFolderMember) {
            return WordUtils.capitalizeFully(((SharedFolderMember)elem)._role.getDescription());
        } else {
            return "";
        }
    }
}
