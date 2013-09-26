package com.aerofs.gui.sharing.manage;

import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import javax.annotation.Nullable;

public class RoleMenu
{
    private final Menu _menu;
    private final UserID _subject;
    private final CompUserList _compUserList;

    public RoleMenu(CompUserList compUserList, SubjectRolePair srp, Control ctrl,
            boolean showUpdateACLMenuItems)
    {
        _menu = new Menu(ctrl);
        _subject = srp._subject;
        _compUserList = compUserList;

        if (showUpdateACLMenuItems) {
            if (srp._role != Role.OWNER) {
                MenuItem miOwner = new MenuItem(_menu, SWT.PUSH);
                miOwner.setText("Owner");
                miOwner.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e)
                    {
                        select(Role.OWNER);
                    }
                });
            }

            if (srp._role != Role.EDITOR) {
                MenuItem miEditor = new MenuItem(_menu, SWT.PUSH);
                miEditor.setText("Editor");
                miEditor.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e)
                    {
                        select(Role.EDITOR);
                    }
                });
            }

            if (srp._role != Role.VIEWER) {
                MenuItem miViewer = new MenuItem(_menu, SWT.PUSH);
                miViewer.setText("Viewer");
                miViewer.addSelectionListener(new SelectionAdapter()
                {
                    @Override
                    public void widgetSelected(SelectionEvent e)
                    {
                        select(Role.VIEWER);
                    }
                });
            }

            new MenuItem(_menu, SWT.SEPARATOR);
        }

        MenuItem miEmail = new MenuItem(_menu, SWT.PUSH);
        miEmail.setText("Email User");
        miEmail.addSelectionListener(GUIUtil.createUrlLaunchListenerr(
                "mailto:" + _subject.getString()));

        if (showUpdateACLMenuItems) {
            MenuItem miKickout = new MenuItem(_menu, SWT.PUSH);
            miKickout.setText("Remove User");
            miKickout.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    if (GUI.get().ask(_compUserList.getShell(), MessageType.QUESTION,
                            // The text should be consistent with the text in shared_folders.mako
                            "Are you sure you want to remove " + _subject +
                                    " from the shared folder?\n" +
                                    "\n" +
                                    "This will delete the folder from the person's computers." +
                                    " However, old content may be still accessible from the" +
                                    " person's sync history.")) {
                        select(null);
                    }
                }
            });
        }
    }

    /**
     * open the menu at the current cursor location
     */
    public void open()
    {
        _menu.setVisible(true);
    }

    public void open(Point leftTopCorner)
    {
        _menu.setLocation(leftTopCorner.x, leftTopCorner.y);
        _menu.setVisible(true);
    }

    /**
     * @param role set to null to remove the user
     */
    private void select(@Nullable Role role)
    {
        _menu.dispose();
        _compUserList.setRole(_subject, role);
    }
}
