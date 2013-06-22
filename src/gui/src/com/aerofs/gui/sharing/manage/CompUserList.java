package com.aerofs.gui.sharing.manage;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.SimpleContentProvider;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;

import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Ritual.GetACLReply;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Table;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.TableColumn;
import org.slf4j.Logger;

import javax.annotation.Nullable;

public class CompUserList extends Composite
{
    private static final Logger l = Loggers.getLogger(CompUserList.class);

    public static interface ILoadListener
    {
        // this method is called within the GUI thread
        void loaded(int memberCount, Role localUserRole);
    }

    private final TableViewer _tv;

    private final TableColumn _tcIcon;
    private final TableColumn _tcSubject;
    private final TableColumn _tcRole;
    private final TableColumn _tcArrow;

    private Path _path;
    private Role _rSelf;

    public CompUserList(Composite parent)
    {
        super(parent, SWT.NONE);

        setLayout(new FillLayout(SWT.HORIZONTAL));
        _tv = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION);
        final Table t = _tv.getTable();
        t.setBackground(getShell().getDisplay().getSystemColor(SWT.COLOR_WHITE));
        t.setHeaderVisible(false);

        ////////
        // add columns

        TableViewerColumn tvcIcon = new TableViewerColumn(_tv, SWT.CENTER);
        tvcIcon.setLabelProvider(new IconLabelProvider());
        _tcIcon = tvcIcon.getColumn();

        TableViewerColumn tvcSubject = new TableViewerColumn(_tv, SWT.NONE);
        tvcSubject.setLabelProvider(new SubjectLabelProvider());
        _tcSubject = tvcSubject.getColumn();

        TableViewerColumn tvcRole = new TableViewerColumn(_tv, SWT.NONE);
        tvcRole.setLabelProvider(new RoleLabelProvider());
        _tcRole = tvcRole.getColumn();

        TableViewerColumn tvcArrow = new TableViewerColumn(_tv, SWT.NONE);
        tvcArrow.setLabelProvider(new ArrowLabelProvider(this));
        _tcArrow = tvcArrow.getColumn();

        ////////
        // table-wise operations

        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent ev)
            {
                ViewerCell vc = _tv.getCell(new Point(ev.x, ev.y));
                if (vc == null) return;

                Object elem = vc.getElement();
                if (!(elem instanceof SubjectRolePair)) return;

                _tv.setSelection(new StructuredSelection(elem), true);

                SubjectRolePair srp = (SubjectRolePair) elem;
                if (!canChangeACL(srp)) return;

                new RoleMenu(CompUserList.this, srp, _tv.getTable()).open();
            }
        });

        _tv.setContentProvider(new SimpleContentProvider());
        _tv.setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer v, Object e1, Object e2)
            {
                return UIUtil.compareUser(((SubjectRolePair) e1)._subject,
                        ((SubjectRolePair) e2)._subject);
            }
        });

        t.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                recalcUserColumnWidth();
            }
        });
    }

    public boolean canChangeACL(SubjectRolePair srp)
    {
        return !srp._subject.equals(Cfg.user()) && _rSelf == Role.OWNER;
    }

    private boolean _recalcing;

    private void recalcUserColumnWidth()
    {
        // this is to prevent an infinite recursion bug found in production
        if (_recalcing) return;

        _recalcing = true;
        try {
            _tcIcon.pack();
            _tcArrow.pack();

            Table t = _tv.getTable();
            ScrollBar sb = t.getVerticalBar();
            int scroll = OSUtil.isOSX() ? 1 : 0;
            scroll += sb != null && sb.isVisible() ? sb.getSize().x : 0;
            int width = t.getBounds().width - 2 * t.getBorderWidth() - _tcIcon.getWidth() -
                    _tcArrow.getWidth() - scroll;
            _tcSubject.setWidth(width * 618 / 1000);
            _tcRole.setWidth(width * 382 / 1000);
        } finally {
            _recalcing = false;
        }
    }

    /**
     * This method can be called in either UI or non-UI threads
     */
    public void load(Path path, final @Nullable ILoadListener listener)
    {
        // TODO: spinner?
        _tv.setInput(new Object[] { S.GUI_LOADING });

        _path = path;
        _rSelf = null;

        Futures.addCallback(UIGlobals.ritualNonBlocking().getACL(Cfg.user().getString(), path.toPB()),
                new FutureCallback<GetACLReply>() {
            @Override
            public void onSuccess(GetACLReply reply)
            {
                try {
                    final Object[] elems = new Object[reply.getSubjectRoleCount()];

                    for (int i = 0; i < elems.length; i++) {
                        PBSubjectRolePair srp = reply.getSubjectRole(i);
                        UserID subject = UserID.fromExternal(srp.getSubject());
                        Role role = Role.fromPB(srp.getRole());

                        elems[i] = new SubjectRolePair(subject, role);

                        if (subject.equals(Cfg.user())) _rSelf = role;
                    }

                    GUI.get().safeAsyncExec(CompUserList.this, new Runnable() {
                        @Override
                        public void run()
                        {
                            _tv.setInput(elems);
                            recalcUserColumnWidth();
                            if (listener != null) listener.loaded(elems.length, _rSelf);
                        }
                    });
                } catch (Exception e) {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            "Failed to retrieve user list: " + e);
                }
            }

            @Override
            public void onFailure(Throwable t)
            {
                GUI.get().safeAsyncExec(CompUserList.this, new Runnable() {
                    @Override
                    public void run()
                    {
                        _tv.setInput(new Object[] {});
                    }
                });

                if (t instanceof ExNoPerm) {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            "You are no longer a member of this shared folder");
                } else {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            "Failed to retrieve user list: " + t);
                }
            }
        });
    }

    public void setRole(UserID subject, Role role)
    {
        try {
            if (role == null) {
                UIGlobals.ritual().deleteACL(Cfg.user().getString(), _path.toPB(),
                        subject.getString());
            } else {
                UIGlobals.ritual().updateACL(Cfg.user().getString(), _path.toPB(), subject.getString(),
                        role.toPB());
            }
            load(_path, null);
        } catch (Exception e) {
            l.warn(Util.e(e));
            GUI.get().show(getShell(), MessageType.ERROR,
                    "Couldn't edit the user. " + S.TRY_AGAIN_LATER + "\n\n" +
                            "Error message: " + ErrorMessages.e2msgSentenceNoBracketDeprecated(e));
        }
    }
}
