package com.aerofs.gui.sharing.members;

class SubjectLabelProvider extends SharingLabelProvider
{
    @Override
    public String getText(Object element)
    {
        if (element instanceof Exception) {
            return ((Exception) element).getMessage();
        } else if (element instanceof SharedFolderMember) {
            return ((SharedFolderMember)element).getLabel();
        } else {
            return element.toString();
        }
    }
}
