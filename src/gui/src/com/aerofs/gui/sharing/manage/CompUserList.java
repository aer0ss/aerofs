package com.aerofs.gui.sharing.manage;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.SimpleContentProvider;
import com.aerofs.gui.sharing.manage.RoleMenu.RoleChangeListener;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Ritual.GetACLReply;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.aerofs.gui.sharing.SharedFolderRulesExceptionHandlers.canHandle;
import static com.aerofs.gui.sharing.SharedFolderRulesExceptionHandlers.promptUserToSuppressWarning;
import static com.google.common.base.Preconditions.checkState;

public class CompUserList extends Composite
{
    private static final Logger l = Loggers.getLogger(CompUserList.class);

    public static interface ILoadListener
    {
        // this method is called within the GUI thread
        void loaded(int memberCount, Role localUserRole);
    }

    private final TableViewer _tv;

    private final TableColumn _tcSubject;
    private final TableColumn _tcRole;
    private final TableColumn _tcArrow;

    private CompSpin _compSpin;

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

        TableViewerColumn tvcSubject = new TableViewerColumn(_tv, SWT.NONE);
        tvcSubject.setLabelProvider(new SubjectLabelProvider());
        _tcSubject = tvcSubject.getColumn();

        TableViewerColumn tvcRole = new TableViewerColumn(_tv, SWT.NONE);
        tvcRole.setLabelProvider(new RoleLabelProvider());
        _tcRole = tvcRole.getColumn();
        _tcRole.setAlignment(SWT.RIGHT);

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
                if (!hasContextMenu(srp)) return;

                RoleMenu menu = new RoleMenu(_tv.getTable(),  srp, shouldShowUpdateACLMenuItems());
                menu.setRoleChangeListener(new RoleChangeListener()
                {
                    @Override
                    public void onRoleChangeSelected(UserID subject, Role role)
                    {
                        setRole(_path, subject, role, false);
                    }
                });
                menu.open();
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

    // the spinner control is a separate public method because this way we can construct the
    // composite in whatever order we want. In practice, it should either be unset or set once and
    // never change.
    public void setSpinner(CompSpin compSpin)
    {
        _compSpin = compSpin;
    }

    /**
     * FIXME There is an edge case, while running team server, that we show the update ACL menu
     * items even though the team server doesn't have the necessary permission to update the ACL.
     *
     * It occurs when the team server sees a particular shared folder because someone in the
     * organization is a member but none of the owners of the said shared folder is in the
     * organization.
     */
    private boolean shouldShowUpdateACLMenuItems()
    {
        return _rSelf == Role.OWNER // regular client
                || L.isMultiuser(); // team server
    }

    public boolean hasContextMenu(SubjectRolePair srp)
    {
        // we have a context menu iff we are not the current user, because we can always send e-mail
        return !srp._subject.equals(Cfg.user());
    }

    private boolean _recalcing;

    private void recalcUserColumnWidth()
    {
        // this is to prevent an infinite recursion bug found in production
        if (_recalcing) return;

        _recalcing = true;
        try {
            _tcArrow.pack();

            Table t = _tv.getTable();
            ScrollBar sb = t.getVerticalBar();
            int scroll = OSUtil.isOSX() ? 1 : 0;
            scroll += sb != null && sb.isVisible() ? sb.getSize().x : 0;
            int width = t.getBounds().width - 2 * t.getBorderWidth() -
                    _tcArrow.getWidth() - scroll;
            _tcSubject.setWidth(width * 618 / 1000);
            _tcRole.setWidth(width * 382 / 1000);
        } finally {
            _recalcing = false;
        }
    }

    /**
     * @pre must be invoked from UI threads.
     */
    public void load(Path path, final @Nullable ILoadListener listener)
    {
        checkState(GUI.get().isUIThread());

        _tv.setInput(new Object[] { S.GUI_LOADING });

        _path = path;
        _rSelf = null;

        Futures.addCallback(UIGlobals.ritualNonBlocking().getACL(path.toPB()),
                new FutureCallback<GetACLReply>()
                {
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

                            GUI.get().safeAsyncExec(CompUserList.this, new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    _tv.setInput(elems);
                                    recalcUserColumnWidth();
                                    if (listener != null) listener.loaded(elems.length, _rSelf);
                                }
                            });
                        } catch (Exception e) {
                            ErrorMessages.show(getShell(), e, "Failed to retrieve user list: " + e);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t)
                    {
                        GUI.get().safeAsyncExec(CompUserList.this, new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                _tv.setInput(new Object[0]);
                            }
                        });

                        ErrorMessages.show(getShell(), t, "Failed to retrieve user list: " + t,
                                new ErrorMessage(ExNoPerm.class,
                                        "You are no longer a member of this shared folder"));
                    }
                });
    }

    /**
     * {@paramref path} needs to be passed in because _path can change while ISWTWorker does work
     */
    private void setRole(final Path path, final UserID subject, final Role role,
            final boolean suppressSharedFolderRulesWarnings)
    {
        final Table table = _tv.getTable();
        table.setEnabled(false);

        if (_compSpin != null) _compSpin.start();

        GUI.get().safeWork(getShell(), new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                if (role == null) {
                    UIGlobals.ritual().deleteACL(path.toPB(), subject.getString());
                } else {
                    UIGlobals.ritual().updateACL(path.toPB(), subject.getString(), role.toPB(),
                            suppressSharedFolderRulesWarnings);
                }
            }

            @Override
            public void okay()
            {
                if (_compSpin != null && !_compSpin.isDisposed()) _compSpin.stop();

                if (!table.isDisposed()) {
                    table.setEnabled(true);
                    load(path, null);
                }
            }

            @Override
            public void error(Exception e)
            {
                if (_compSpin != null && !_compSpin.isDisposed()) _compSpin.stop();

                if (!table.isDisposed()) table.setEnabled(true);

                l.warn(Util.e(e));

                if (canHandle(e)) {
                    if (promptUserToSuppressWarning(getShell(), e)) {
                        setRole(path, subject, role, true);
                    }
                } else {
                    String message = "Couldn't edit the user. " + S.TRY_AGAIN_LATER + "\n\n" +
                            "Error message: " + ErrorMessages.e2msgSentenceNoBracketDeprecated(e);

                    ErrorMessages.show(getShell(), e, "Unused default.",
                            new ErrorMessage(e.getClass(), message));
                }
            }
        });
    }
}
