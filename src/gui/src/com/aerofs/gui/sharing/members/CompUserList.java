package com.aerofs.gui.sharing.members;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.sharing.members.SharedFolderMember.User;
import com.aerofs.gui.sharing.members.SharingLabelProvider.ArrowLabelProvider;
import com.aerofs.gui.sharing.members.SharingLabelProvider.RoleLabelProvider;
import com.aerofs.gui.sharing.members.SharingLabelProvider.SubjectLabelProvider;
import com.aerofs.gui.sharing.members.SharingModel.LoadResult;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static java.util.Collections.emptyList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

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

    private final SharingModel _model;

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

        _model = new SharingModel(UIGlobals.ritualClientProvider(), newMutualAuthClientFactory(),
                new Factory(new CfgLocalUser()), listeningDecorator(newSingleThreadExecutor()));

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
        tvcArrow.setLabelProvider(new ArrowLabelProvider(this));
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
                SharedFolderMemberMenu menu =
                        SharedFolderMemberMenu.get(_localUserPermissions, member);

                if (!menu.hasContextMenu()) return;

                menu.setRoleChangeListener(
                        permissions -> setRole(_sid, member, permissions, false));
                menu.open(_tv.getTable());
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

        if (path == null) {
            setState("");
            return;
        }

        setState(S.GUI_LOADING);

        addCallback(_model.load(path), new FutureCallback<LoadResult>() {
            @Override
            public void onSuccess(@Nullable LoadResult result)
            {
                setState(result._members, result._permissions, result._sid);
                layoutTableColumns();
            }

            @Override
            public void onFailure(Throwable t)
            {
                setState("");

                ErrorMessages.show(getShell(), t, "Failed to retrieve user list.",
                        new ErrorMessage(ExNoPerm.class,
                                "You are no longer a member of this shared folder."),
                        new ErrorMessage(ExBadArgs.class,
                                "The application has received invalid data from the server."));
            }
        }, new GUIExecutor(_tv.getTable()));
    }

    /**
     * {@paramref sid} needs to be passed in because _sid can change while ISWTWorker does work
     */
    private void setRole(final SID sid, final SharedFolderMember member,
            final Permissions permissions, final boolean suppressSharedFolderRulesWarnings)
    {
        Table table = _tv.getTable();
        table.setEnabled(false);

        if (_compSpin != null) _compSpin.start();

        addCallback(_model.setRole(sid, member, permissions, suppressSharedFolderRulesWarnings),
                new FutureCallback<Void>()
                {
                    @Override
                    public void onSuccess(@Nullable Void result)
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
                    public void onFailure(Throwable t)
                    {
                        if (_compSpin != null && !_compSpin.isDisposed()) _compSpin.stop();

                        if (!table.isDisposed()) table.setEnabled(true);

                        l.warn(Util.e(t));

                        if (canHandle(t)) {
                            if (promptUserToSuppressWarning(getShell(), t)) {
                                setRole(sid, member, permissions, true);
                            }
                        } else {
                            String message = String.format(permissions == null
                                            ? "Failed to remove the %s."
                                            : "Failed to update the %s's role.",
                                    member instanceof User ? "user" : "group");
                            ErrorMessages.show(getShell(), t, message,
                                    new ErrorMessage(ExNoPerm.class,
                                            "You do not have the permission to do so."));
                        }
                    }
                }, new GUIExecutor(table));
    }
}
