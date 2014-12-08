package com.aerofs.gui.sharing.members;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.sharing.members.SharingLabelProvider.ArrowLabelProvider;
import com.aerofs.gui.sharing.members.SharingLabelProvider.RoleLabelProvider;
import com.aerofs.gui.sharing.members.SharingLabelProvider.SubjectLabelProvider;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Ritual;
import com.aerofs.proto.Sp;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.proto.Sp.PBSharedFolderState;
import com.aerofs.sp.client.SPBlockingClient;
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

import static com.aerofs.gui.sharing.SharingRulesExceptionHandlers.canHandle;
import static com.aerofs.gui.sharing.SharingRulesExceptionHandlers.promptUserToSuppressWarning;
import static com.aerofs.gui.sharing.members.SharedFolderMember.Factory;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static java.util.Collections.emptyList;

/**
 * SharedFolder and ACL are terms that are closely related: SharedFolder is the product feature to
 * meet the business requirements, whereas ACL is the implementation detail to facilitate syncing.
 *
 * For the most part, the GUI client will talk to SP to do any shared folder-related operations.
 * The daemon will then discover any ACL changes from ACLSynchronizer.
 */
public class CompUserList extends Composite
{
    private static final Logger l = Loggers.getLogger(CompUserList.class);

    private final TableViewer _tv;

    private final TableColumn _tcSubject;
    private final TableColumn _tcRole;
    private final TableColumn _tcArrow;

    private CompSpin _compSpin;

    /**
     * In general, these 3 states need to stay in sync and should all be updated at the same time
     * on the UI thread via one of the setState() methods.
     *
     * These states are public for others to read. They should not be mutated by any other objects.
     *
     * The content of _members can be accessed and updated directly, but it should be done on the
     * UI thread to avoid a race against the UI thread.
     */
    public List<SharedFolderMember> _members = emptyList();
    public @Nullable Permissions _localUserPermissions;
    public @Nullable SID _sid;

    public interface StateChangedListener
    {
        void onStateChanged();
    }
    private StateChangedListener _stateChangedListener;

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
                menu.setRoleChangeListener(
                        permissions -> setRole(_sid, member, permissions, false));
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

    // pre: must be called from UI thread and members must not be null.
    private void setState(List<SharedFolderMember> members, Permissions localUserPermissions,
            SID sid)
    {
        setStateImpl(members, members, localUserPermissions, sid);
    }

    // pre: must be called from the UI thread
    private void setState(String errorMessage)
    {
        setStateImpl(newArrayList(errorMessage), emptyList(), null, null);
    }

    // pre: must be called from the UI thread and members must not be null
    private void setStateImpl(Object input, List<SharedFolderMember> members,
            @Nullable Permissions localUserPermissions, @Nullable SID sid)
    {
        checkState(GUI.get().isUIThread());
        checkNotNull(members);

        _members = members;
        _localUserPermissions = localUserPermissions;
        _sid = sid;

        // N.B. this establishes a tight binding; if the data in _members is updated, the changes
        // will be visible after _tv refreshes.
        _tv.setInput(input);

        notifyStateChangedListener();
    }

    public void setStateChangedListener(@Nullable StateChangedListener listener)
    {
        _stateChangedListener = listener;
    }

    private void notifyStateChangedListener()
    {
        if (_stateChangedListener != null) {
            _stateChangedListener.onStateChanged();
        }
    }

    /**
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
            SID _newSID;

            @Override
            public void run()
                    throws Exception
            {
                if (path == null) return;

                CfgLocalUser localUser = new CfgLocalUser();
                _newSID = getStoreID(path);
                Sp.PBSharedFolder pbFolder = getSharedFolderWithSID(_newSID);

                _newMembers = createListFromPBAndFilterLeftMembers(new Factory(localUser),
                        pbFolder);

                // N.B. the local user may not be a member, e.g. Team Server
                _newLocalUserPermissions = _newMembers.stream()
                        .filter(SharedFolderMember::isLocalUser)
                        .findAny()
                        .map(member -> member._permissions)
                        .orElse(null);
            }

            // makes a ritual call to determine the SID for a given path to a shared folder.
            private SID getStoreID(Path path) throws Exception
            {
                Ritual.ListSharedFoldersReply reply = UIGlobals.ritual().listSharedFolders();

                for (Ritual.PBSharedFolder folder : reply.getSharedFolderList()) {
                    if (path.equals(Path.fromPB(folder.getPath()))) {
                        return new SID(folder.getStoreId());
                    }
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
                setState(_newMembers, _newLocalUserPermissions, _newSID);

                layoutTableColumns();
            }

            @Override
            public void error(Exception e)
            {
                setState("");

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
     * {@paramref sid} needs to be passed in because _sid can change while ISWTWorker does work
     */
    private void setRole(final SID sid, final SharedFolderMember member,
            final Permissions permissions, final boolean suppressSharedFolderRulesWarnings)
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
                SPBlockingClient sp = newMutualAuthClientFactory().create().signInRemote();

                if (permissions == null) {
                    sp.deleteACL(sid.toPB(), member.getSubject());
                } else {
                    sp.updateACL(sid.toPB(), member.getSubject(), permissions.toPB(),
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

                    if (permissions == null) {
                        _members.remove(member);
                    } else {
                        member._permissions = permissions;
                    }

                    _tv.refresh();

                    notifyStateChangedListener();
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
                        setRole(sid, member, permissions, true);
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
