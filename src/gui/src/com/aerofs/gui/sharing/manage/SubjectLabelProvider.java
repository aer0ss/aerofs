package com.aerofs.gui.sharing.manage;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.lib.cfg.Cfg;

/**
 * TODO use StyleCellLabelProvider to display subject (in black) + role (in grey) in a single cell.
 * See http://www.vogella.com/articles/EclipseJFaceTree/article.html
 */
class SubjectLabelProvider extends ColumnLabelProvider
{
    @Override
    public String getText(Object element)
    {
        if (element instanceof Exception) {
            return ((Exception) element).getMessage();
        } else if (element instanceof SubjectRolePair) {
            SubjectRolePair srp = (SubjectRolePair) element;
            return srp._subject.equals(Cfg.user()) ? "me" : srp._subject.getString();
        } else {
            return element.toString();
        }
    }
}