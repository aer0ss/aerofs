package com.aerofs.gui.sharing.manage;

import java.util.Collections;

import javax.annotation.Nullable;

import com.aerofs.lib.Param.SP;
import com.aerofs.lib.Path;
import com.aerofs.lib.Role;
import com.aerofs.lib.S;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;

import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.aerofs.gui.GUI;
import com.aerofs.gui.sharing.CompInviteUsers;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;

public class RoleMenu
{
    private static final Logger l = Util.l(RoleMenu.class);
    private final Path _path;
    private final Menu _menu;
    private final String _subject;
    private final Role _role;
    private final CompUserList _compUserList;

    public RoleMenu(CompUserList compUserList, SubjectRolePair srp, Control ctrl, Path path)
    {
        _menu = new Menu(ctrl);
        _path = path;
        _subject = srp._subject;
        _role = srp._role;
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
            miEditor.setText("Make Editor");
            miEditor.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    select(Role.EDITOR);
                }
            });
        }

        MenuItem miReinvite = new MenuItem(_menu, SWT.PUSH);
        miReinvite.setText("Reinvite");
        miReinvite.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                reinvite();
            }
        });

        new MenuItem(_menu, SWT.SEPARATOR);

        MenuItem miNone = new MenuItem(_menu, SWT.PUSH);
        miNone.setText("Kickout");
        miNone.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (GUI.get().ask(_compUserList.getShell(), MessageType.QUESTION,
                        "Are you sure you want to remove " + _subject + "?")) {
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

    private void reinvite()
    {
        try {
            // TODO: All this just to get fromPerson... fix this once we cache user preferences
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            String fromPerson = sp.getPreferences(Cfg.did().toPB()).getFirstName();

            String note = CompInviteUsers.getDefaultInvitationNote(_path.last(), fromPerson);
            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
            try {
                ritual.shareFolder(Cfg.user(), _path.toPB(), Collections.singletonList(
                        new SubjectRolePair(_subject, _role).toPB()), note);
            } finally {
                ritual.close();
            }

            GUI.get().show(_compUserList.getShell(), MessageType.INFO, _subject +
                    " was reinvited successfully.");

        } catch (Exception e) {
            GUI.get().show(_compUserList.getShell(), MessageType.ERROR, "Could not reinvite" +
                    " the user " + UIUtil.e2msg(e));
        }
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
                    ritual.deleteACL(Cfg.user(), _path.toPB(), Collections.singletonList(_subject));
                } else {
                    SubjectRolePair srp = new SubjectRolePair(_subject, role);
                    ritual.updateACL(Cfg.user(), _path.toPB(),
                            Collections.singletonList(srp.toPB()));
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
