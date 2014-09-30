package com.aerofs.gui.sharing.members;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.sharing.members.RoleMenu.RoleChangeListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Ritual;
import com.aerofs.proto.Sp;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.proto.Sp.PBSharedFolderState;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.collect.ImmutableList;
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
import java.util.List;
import java.util.Optional;

import static com.aerofs.gui.sharing.SharingRulesExceptionHandlers.canHandle;
import static com.aerofs.gui.sharing.SharingRulesExceptionHandlers.promptUserToSuppressWarning;
import static com.aerofs.gui.sharing.members.SharedFolderMember.Factory;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static java.util.Collections.emptyList;

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
        // N.B. local user permissions may be null, e.g. Team Server
        void loaded(int memberCount, @Nullable Permissions localUserPermissions);
    }

    private ILoadListener _listener;

    private final TableViewer _tv;

    private final TableColumn _tcSubject;
    private final TableColumn _tcRole;
    private final TableColumn _tcArrow;

    private CompSpin _compSpin;

    /**
     * In general, these 3 states need to stay in sync and should all be updated at the same time
     * on the UI thread via one of the setState() methods.
     *
     * The content of _members can be accessed and updated directly, but it should be done on the
     * UI thread to avoid a race against the UI thread.
     */
    // _members is cached because we want to look up a member by user ID in setRole().
    private List<SharedFolderMember> _members = emptyList();
    private Permissions _localUserPermissions;
    private Path _path;

    public CompUserList(Composite parent)
    {
        super(parent, SWT.NONE);

        _tv = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION);
        _tv.setContentProvider(new ArrayContentProvider());
        _tv.setComparator(SharedFolderMemberComparators.bySubject());
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
                _tv.setComparator(SharedFolderMemberComparators.bySubject());
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
                _tv.setComparator(SharedFolderMemberComparators.byRole());
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

                RoleMenu menu = new RoleMenu(_tv.getTable(), _localUserPermissions, member);
                menu.setRoleChangeListener(new RoleChangeListener()
                {
                    @Override
                    public void onRoleChangeSelected(UserID subject, Permissions permissions)
                    {
                        setRole(_path, subject, permissions, false);
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
        layout(new Control[]{_tv.getTable()});
    }

    /**
     * @pre must be called from UI thread and members must not be null.
     */
    private void setState(List<SharedFolderMember> members, Permissions localUserPermissions, Path path)
    {
        checkState(GUI.get().isUIThread());
        checkNotNull(members);

        _members = members;
        _localUserPermissions = localUserPermissions;
        _path = path;

        // N.B. this establishes a tight binding; if the data in _members is updated, the changes
        // will be visible after _tv refreshes.
        _tv.setInput(_members);
    }

    /**
     * @pre must be called from UI thread.
     */
    private void setState(String errorMessage)
    {
        checkState(GUI.get().isUIThread());

        _members.clear();
        _localUserPermissions = null;
        _path = null;

        _tv.setInput(new String[] { errorMessage } );
    }

    public void setLoadListener(@Nullable ILoadListener listener)
    {
        _listener = listener;
    }

    /**
     * N.B. the value will be fetched from the current state
     * @pre must be invoked from the UI thread and the current state must have members
     */
    private void notifyLoadListener()
    {
        if (_listener != null) _listener.loaded(_members.size(), _localUserPermissions);
    }

    /**
     * {@paramref path} must be passed in because _path can change while the worker does work
     *
     * @pre must be invoked from the UI thread.
     */
    public void load(final Path path)
    {
        checkState(GUI.get().isUIThread());

        setState(S.GUI_LOADING);

        GUI.get().safeWork(_tv.getTable(), new ISWTWorker()
        {
            List<SharedFolderMember> _newMembers = emptyList();
            Permissions _newLocalUserPermissions;

            @Override
            public void run()
                    throws Exception
            {
                if (path == null) return;

                CfgLocalUser localUser = new CfgLocalUser();
                SID sid = getStoreID(path);
                Sp.PBSharedFolder pbFolder = getSharedFolderWithSID(sid);

                _newMembers = createListFromPBAndFilterLeftMembers(new Factory(localUser),
                        pbFolder);

                // N.B. the local user may not be a member, e.g. Team Server
                Optional<SharedFolderMember> localUserAsMember = _newMembers.stream()
                        .filter(SharedFolderMember::isLocalUser)
                        .findAny();
                if (localUserAsMember.isPresent()) {
                    _newLocalUserPermissions = localUserAsMember.get()._permissions;
                }
            }

            // makes a ritual call to retrieve the list of shared folders and their sids and
            // find the sid for the given path.
            private SID getStoreID(Path path) throws Exception
            {
                Ritual.ListSharedFoldersReply reply = UIGlobals.ritual().listSharedFolders();

                for (Ritual.PBSharedFolder folder : reply.getSharedFolderList()) {
                    if (path.equals(Path.fromPB(folder.getPath())))
                    return new SID(folder.getStoreId());
                }

                throw new ExBadArgs("Invalid shared folder.");
            }

            // make a SP call to retrieve the shared folder associated with the store ID
            // @param userID - the user ID of the local user
            private Sp.PBSharedFolder getSharedFolderWithSID(SID sid)
                    throws Exception
            {
                Sp.ListSharedFoldersReply reply = newMutualAuthClientFactory().create()
                        .signInRemote()
                        .listSharedFolders(ImmutableList.of(sid.toPB()));
                // assert the contract of the sp call
                checkArgument(reply.getSharedFolderCount() == 1);

                return reply.getSharedFolder(0);
            }

            private List<SharedFolderMember> createListFromPBAndFilterLeftMembers(
                    Factory factory, Sp.PBSharedFolder pbFolder) throws ExBadArgs
            {
                List<SharedFolderMember> members = newArrayListWithExpectedSize(
                        pbFolder.getUserPermissionsAndStateCount());

                for (PBUserPermissionsAndState urs : pbFolder.getUserPermissionsAndStateList()) {
                    if (urs.getState() != PBSharedFolderState.LEFT) {
                        members.add(factory.fromPB(urs));
                    }
                }

                return members;
            }

            @Override
            public void okay()
            {
                setState(_newMembers, _newLocalUserPermissions, path);
                notifyLoadListener();

                layoutTableColumns();
            }

            @Override
            public void error(Exception e)
            {
                setState("");
                notifyLoadListener();

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
    private void setRole(final Path path, final UserID subject, final Permissions permissions,
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
                if (permissions == null) {
                    UIGlobals.ritual().deleteACL(path.toPB(), subject.getString());
                } else {
                    UIGlobals.ritual().updateACL(path.toPB(), subject.getString(), permissions.toPB(),
                            suppressSharedFolderRulesWarnings);
                }
            }

            @Override
            public void okay()
            {
                if (_compSpin != null && !_compSpin.isDisposed()) _compSpin.stop();

                if (!table.isDisposed()) {
                    table.setEnabled(true);
                    table.setFocus();

                    SharedFolderMember member = _members.stream()
                            .filter(x -> x._userID.equals(subject))
                            .findFirst().get();

                    if (permissions == null) {
                        _members.remove(member);
                    } else {
                        member._permissions = permissions;
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
                        setRole(path, subject, permissions, true);
                    }
                } else {
                    String message = permissions == null ? "Failed to remove the user." :
                            "Failed to update the user's role.";
                    ErrorMessages.show(getShell(), e, message,
                            new ErrorMessage(ExNoPerm.class,
                                    "You do not have the permission to do so."));
                }
            }
        });
    }
}
