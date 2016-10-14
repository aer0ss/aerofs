/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing.invitee;

import com.aerofs.base.acl.Permissions;
import com.aerofs.gui.AeroFSButton;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;

public class ComboRoles extends AeroFSButton
{
    private Permissions _permissions;

    public ComboRoles(Composite parent)
    {
        super(parent, SWT.PUSH);

        addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                Menu menu = new Menu(ComboRoles.this);

                for (Permissions r : Permissions.ROLE_NAMES.keySet()) {
                    MenuItem mi = new MenuItem(menu, SWT.PUSH);
                    mi.setText(r.roleName() + " - " + r.roleDescription());
                    mi.addSelectionListener(createSelector(r));
                }

                new MenuItem(menu, SWT.SEPARATOR);

                MenuItem miExplain = new MenuItem(menu, SWT.PUSH);
                miExplain.setText("Learn more about roles");
                miExplain.addSelectionListener(createUrlLaunchListener(S.URL_ROLES));

                menu.setVisible(true);
            }
        });
    }

    public void selectRole(Permissions permissions)
    {
        _permissions = permissions;
        setText(_permissions.roleName() + ' ' + GUIUtil.TRIANGLE_DOWNWARD);
    }

    private SelectionListener createSelector(final Permissions permissions)
    {
        return new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                selectRole(permissions);
            }
        };
    }

    public Permissions getSelectedRole()
    {
        return _permissions;
    }
}
