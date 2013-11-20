package com.aerofs.gui.sharing.manage;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.sharing.manage.RoleMenu.RoleChangeListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Ritual;
import com.aerofs.proto.Sp;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserRoleAndState;
import com.aerofs.proto.Sp.PBSharedFolderState;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.util.Collection;

import static com.aerofs.gui.sharing.SharedFolderRulesExceptionHandlers.canHandle;
import static com.aerofs.gui.sharing.SharedFolderRulesExceptionHandlers.promptUserToSuppressWarning;
import static com.aerofs.gui.sharing.manage.SharedFolderMember.Factory;
import static com.google.common.base.Preconditions.checkState;

/**
 * SharedFolder and ACL are terms that are closely related: SharedFolder is the product feature to
 * meet the business requirements, whereas ACL is the implementation detail to facilitate syncing.
 *
 * A key decision to adding support for showing pending users is that we want to keep ACL as is,
 * the bare minimum information required to facilitate syncing. Thus the pending user information
 * was not propagated to ACLSynchronizers via GetACL. Instead, it was exposed via the
 * ListUserSharedFolders call on SP.
 *
 * Previously, CompUserList only talks to the daemon. It obtains and updates shared folder info
 * from the daemon in the form of ACLs & SubjectRolePairs, which does not contain pending users.
 * Thus, CompUserList now queries SP directly for shared folder information.
 */
public class CompUserList extends Composite
{
    private static final Logger l = Loggers.getLogger(CompUserList.class);

    public interface ILoadListener
    {
        // this method is called within the GUI thread
        void loaded(int memberCount, Role localUserRole);
    }

    private ILoadListener _listener;

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

        _tv = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION);
        _tv.setContentProvider(new ArrayContentProvider());
        _tv.setComparator(SharedFolderMemberComparator.bySubject());
        ColumnViewerToolTipSupport.enableFor(_tv);

        final Table table = _tv.getTable();
        table.setBackground(getShell().getDisplay().getSystemColor(SWT.COLOR_WHITE));
        table.setHeaderVisible(true);
        table.setLinesVisible(false);

        ////////
        // add columns
        TableViewerColumn tvcSubject = new TableViewerColumn(_tv, SWT.NONE);
        tvcSubject.setLabelProvider(new SubjectLabelProvider());
        _tcSubject = tvcSubject.getColumn();
        _tcSubject.setText("Name");
        _tcSubject.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                _tv.setComparator(SharedFolderMemberComparator.bySubject());
            }
        });

        TableViewerColumn tvcRole = new TableViewerColumn(_tv, SWT.NONE);
        tvcRole.setLabelProvider(new RoleLabelProvider());
        _tcRole = tvcRole.getColumn();
        _tcRole.setText("Role");
        _tcRole.setAlignment(SWT.RIGHT);
        _tcRole.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                _tv.setComparator(SharedFolderMemberComparator.byRole());
            }
        });

        TableViewerColumn tvcArrow = new TableViewerColumn(_tv, SWT.NONE);
        tvcArrow.setLabelProvider(new ArrowLabelProvider());
        _tcArrow = tvcArrow.getColumn();
        _tcArrow.setResizable(false);

        setLayout(new TableColumnLayout());
        layoutTableColumns();

        ////////
        // table-wise operations

        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseUp(MouseEvent ev)
            {
                ViewerCell vc = _tv.getCell(new Point(ev.x, ev.y));
                if (vc == null) return;

                Object elem = vc.getElement();
                if (!(elem instanceof SharedFolderMember)) return;

                _tv.setSelection(new StructuredSelection(elem), true);

                SharedFolderMember member = (SharedFolderMember)elem;
                if (!RoleMenu.hasContextMenu(member)) return;

                RoleMenu menu = new RoleMenu(_tv.getTable(), _rSelf, member);
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
    }

    // the spinner control is a separate public method because this way we can construct the
    // composite in whatever order we want. In practice, it should either be unset or set once and
    // never change.
    public void setSpinner(CompSpin compSpin)
    {
        _compSpin = compSpin;
    }

    // updates the layout of table columns, should be called every time the width of _tcRole
    // or _tcArrow changes
    private void layoutTableColumns()
    {
        _tcArrow.pack();

        // use golden ratio
        TableColumnLayout layout = (TableColumnLayout) getLayout();
        layout.setColumnData(_tcSubject, new ColumnWeightData(618));
        layout.setColumnData(_tcRole, new ColumnWeightData(382));
        layout.setColumnData(_tcArrow, new ColumnWeightData(0, _tcArrow.getWidth()));
        layout(new Control[]{ _tv.getTable() });
    }

    /**
     * In general, these 3 states need to stay in sync and should all be updated at the same time
     *
     * @pre must be called from UI thread so we can serialize the write access to these 3 states.
     */
    private void setState(Object input, Role selfRole, Path path)
    {
        checkState(UI.isGUI());

        _tv.setInput(input);
        _rSelf = selfRole;
        _path = path;
    }

    public void setLoadListener(@Nullable ILoadListener listener)
    {
        _listener = listener;
    }

    /**
     * N.B. the value will be fetched from the table's data
     *
     * @pre must be invoked from the UI thread and the table must contain valid data.
     */
    private void notifyLoadListener()
    {
        throwIfNoTableData();
        if (_listener != null) _listener.loaded(getTableData().size(), _rSelf);
    }

    /**
     * @pre must be invoked from the UI thread and the table must have data
     */
    @SuppressWarnings("unchecked")
    private Collection<SharedFolderMember> getTableData()
    {
        throwIfNoTableData();
        return (Collection<SharedFolderMember>)_tv.getInput();
    }

    private void throwIfNoTableData()
    {
        checkState(GUI.get().isUIThread() && _tv.getInput() instanceof Collection);
    }

    /**
     * {@paramref path} must be passed in because _path can change while the worker does work
     *
     * @pre must be invoked from the UI thread.
     */
    public void load(final Path path)
    {
        checkState(GUI.get().isUIThread());

        setState(new Object[]{S.GUI_LOADING}, null, null);

        GUI.get().safeWork(_tv.getTable(), new ISWTWorker()
        {
            Collection<SharedFolderMember> _members;
            Role _localUserRole;

            @Override
            public void run()
                    throws Exception
            {
                CfgLocalUser localUser = new CfgLocalUser();
                SID sid = getStoreID(path);
                Sp.PBSharedFolder pbFolder = getSharedFolderWithSID(localUser, sid);
                Factory factory = new Factory(localUser);

                _members = createMembers(factory, pbFolder);
                _localUserRole = findLocalUserRoleNullable(_members);
            }

            // makes a ritual call to retrieve the list of shared folders and their sids and
            // find the sid for the given path.
            private SID getStoreID(Path path) throws Exception
            {
                Ritual.ListSharedFoldersReply reply = UIGlobals.ritual().listSharedFolders();

                for (Ritual.PBSharedFolder folder : reply.getSharedFolderList()) {
                    if (path.equals(Path.fromPB(folder.getPath()))) {
                        return new SID(folder.getStoreId());
                    }
                }

                throw new ExBadArgs("Invalid shared folder path.");
            }

            // make a SP call to retrieve the shared folder associated with the store ID
            // @param userID - the user ID of the local user
            private Sp.PBSharedFolder getSharedFolderWithSID(CfgLocalUser localUser, SID sid)
                    throws Exception
            {
                SPBlockingClient sp = new SPBlockingClient.Factory().create_(localUser.get());
                sp.signInRemote();

                Sp.ListSharedFoldersReply reply =
                        sp.listSharedFolders(ImmutableList.of(sid.toPB()));
                // assert the contract of the sp call
                Preconditions.checkArgument(reply.getSharedFolderCount() == 1);

                return reply.getSharedFolder(0);
            }

            // filters out the members of PBSharedFolder that have left and then map the remaining
            // members to SharedFolderMembers;
            private Collection<SharedFolderMember> createMembers(Factory factory,
                    Sp.PBSharedFolder pbFolder) throws ExBadArgs
            {
                // using a set here to remove duplicates
                Collection<SharedFolderMember> members =
                        Sets.newHashSetWithExpectedSize(pbFolder.getUserRoleAndStateCount());

                for (PBUserRoleAndState urs : pbFolder.getUserRoleAndStateList()) {
                    if (urs.getState() != PBSharedFolderState.LEFT) {
                        members.add(factory.fromPB(urs));
                    }
                }

                return members;
            }

            // returns null if the local user is not found (not a member / team server)
            @Nullable
            private Role findLocalUserRoleNullable(Collection<SharedFolderMember> members)
            {
                for (SharedFolderMember member : members) {
                    if (member.isLocalUser()) return member._role;
                }

                return null;
            }

            @Override
            public void okay()
            {
                setState(_members, _localUserRole, path);

                layoutTableColumns();

                notifyLoadListener();
            }

            @Override
            public void error(Exception e)
            {
                setState(new Object[0], null, null);

                ErrorMessages.show(getShell(), e, "Failed to retrieve user list.",
                        new ErrorMessage(ExNoPerm.class,
                                "You are no longer a member of this shared folder."),
                        new ErrorMessage(ExBadArgs.class,
                                "The application has received invalid data from the server.")
                );
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

                    try {
                        updateRoleInLocalCache(subject, role);
                    } catch (Exception e) {
                        // this is unexpected; we should log and fallback to loading from SP
                        String message = e instanceof ExNotFound ?
                                "Failed to locally update the shared folder member's role: " +
                                        "cannot find the user " + subject :
                                "Failed to locally update the shared folder member's role.";
                        l.error(message, e);

                        load(path);
                        return; // early exit because load will refresh and notify listeners.
                    }

                    _tv.refresh();

                    notifyLoadListener();
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
                    String message = role == null ? "Failed to remove the user." :
                            "Failed to update the user's role.";
                    ErrorMessages.show(getShell(), e, message,
                            new ErrorMessage(ExNoPerm.class,
                                    "You do not have the permission to do so."));
                }
            }

            /**
             * Update the user's role to the local cache without making a round trip to SP.
             *
             * @param role - the new role for the user. if null, the user will be removed.
             *
             * @pre must be called from the UI thread and the table must have data
             * @throws ExNotFound if the user cannot be found
             */
            private void updateRoleInLocalCache(UserID userID, Role role) throws ExNotFound
            {
                throwIfNoTableData();

                Collection<SharedFolderMember> members = getTableData();
                SharedFolderMember member = findMemberWithUserIDThrows(members, userID);

                members.remove(member);

                if (role != null) {
                    Factory factory = new Factory(new CfgLocalUser());
                    SharedFolderMember newMember = factory.create(member._userID,
                            member._firstname, member._lastname, role, member._state);
                    members.add(newMember);
                }
            }

            /**
             * @return the shared folder member with the given user ID.
             * @throws ExNotFound if the user cannot be found
             */
            private SharedFolderMember findMemberWithUserIDThrows(
                    Collection<SharedFolderMember> members, UserID userID)
                    throws ExNotFound
            {
                for (SharedFolderMember member : members) {
                    if (member._userID.equals(userID)) return member;
                }

                throw new ExNotFound();
            }
        });
    }
}
