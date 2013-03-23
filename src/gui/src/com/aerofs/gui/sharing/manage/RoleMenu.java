package com.aerofs.gui.sharing.manage;

import javax.annotation.Nullable;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.S;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;

import org.slf4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.aerofs.gui.GUI;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;

public class RoleMenu
{
    private static final Logger l = Loggers.getLogger(RoleMenu.class);
    private final Path _path;
    private final Menu _menu;
    private final UserID _subject;
    private final CompUserList _compUserList;

    public RoleMenu(CompUserList compUserList, SubjectRolePair srp, Control ctrl, Path path)
    {
        _menu = new Menu(ctrl);
        _path = path;
        _subject = srp._subject;
        _compUserList = compUserList;

        if (srp._role != Role.OWNER) {
            MenuItem miOwner = new MenuItem(_menu, SWT.PUSH);
            miOwner.setText("Make Owner");
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
            miEditor.setText("Remove as Owner");
            miEditor.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    select(Role.EDITOR);
                }
            });
        }

        MenuItem miKickout = new MenuItem(_menu, SWT.PUSH);
        miKickout.setText("Remove");
        miKickout.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (GUI.get().ask(_compUserList.getShell(), MessageType.QUESTION,
                        // The text should be consistent with the text in shared_folders.mako
                        "Are you sure you want to remove " + _subject + " from the shared folder?\n" +
                        "\n" +
                        "This will delete the folder from the person's computers." +
                        " However, old content may be still accessible from the" +
                        " person's version history.")) {
                    select(null);
                }
            }
        });
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
        try {
            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
            try {
                if (role == null) {
                    ritual.deleteACL(Cfg.user().getString(), _path.toPB(),
                            _subject.getString());
                } else {
                    ritual.updateACL(Cfg.user().getString(), _path.toPB(), _subject.getString(),
                            role.toPB());
                }
                _compUserList.load(ritual);

            } finally {
                ritual.close();
            }

        } catch (Exception e) {
            l.warn(Util.e(e));
            GUI.get().show(_compUserList.getShell(), MessageType.ERROR,
                    "Couldn't edit the user. " + S.TRY_AGAIN_LATER + "\n\n" +
                    "Error message: " + UIUtil.e2msgSentenceNoBracket(e));
        }
    }
}
