package com.aerofs.gui.sharing.manage;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import com.aerofs.lib.Role;
import com.aerofs.lib.SubjectRolePair;

/**
 * TODO use StyleCellLabelProvider to display subject (in black) + role (in grey) in a single cell.
 * See http://www.vogella.com/articles/EclipseJFaceTree/article.html
 */
class RoleLabelProvider extends ColumnLabelProvider
{
	private final Color _foreground = Display.getCurrent().getSystemColor(SWT.COLOR_GRAY);

    @Override
	public String getText(Object elem)
	{
		if (!(elem instanceof SubjectRolePair)) return "";

        SubjectRolePair srp = (SubjectRolePair) elem;
        return srp._role == Role.EDITOR ? null :
            "(" + srp._role.getDescription().toLowerCase() + ")";
	}

	@Override
	public Color getForeground(Object elem)
	{
	    return _foreground ;
	}
}
