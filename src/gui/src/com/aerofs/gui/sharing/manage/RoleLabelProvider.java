package com.aerofs.gui.sharing.manage;

import com.aerofs.base.acl.SubjectRolePair;
import org.apache.commons.lang.WordUtils;
import org.eclipse.jface.viewers.ColumnLabelProvider;

/**
 * TODO use StyleCellLabelProvider to display subject (in black) + role (in grey) in a single cell.
 * See http://www.vogella.com/articles/EclipseJFaceTree/article.html
 */
class RoleLabelProvider extends ColumnLabelProvider
{
    @Override
    public String getText(Object elem)
    {
        if (elem instanceof SubjectRolePair) {
            SubjectRolePair srp = (SubjectRolePair) elem;
            return WordUtils.capitalizeFully(srp._role.getDescription());
        } else {
            return "";
        }
    }
}
