/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.conflicts;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.common.PathLabelProvider;
import com.aerofs.gui.conflicts.ConflictsModel.Conflict;
import com.aerofs.gui.conflicts.DlgConflicts.IConflictEventListener;
import com.aerofs.lib.Path;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

class LstConflicts extends Composite
{
    private final ConflictsModel _model;

    private final TableViewer _viewer;
    private final Table _table;

    private IConflictEventListener _listener;

    public LstConflicts(Composite parent, ConflictsModel model)
    {
        super(parent, SWT.NONE);

        _model = model;

        _viewer = new TableViewer(this, SWT.BORDER);
        _viewer.setContentProvider(new ArrayContentProvider());

        _table = _viewer.getTable();
        _table.setLinesVisible(false);

        TableViewerColumn colConflicts = new TableViewerColumn(_viewer, SWT.NONE);
        colConflicts.setLabelProvider(new ConflictLabelProvider(colConflicts));

        TableColumnLayout layout = new TableColumnLayout();
        layout.setColumnData(colConflicts.getColumn(), new ColumnWeightData(1));
        setLayout(layout);

        final IRitualNotificationListener notificationListener = new IRitualNotificationListener()
        {
            @Override
            public void onNotificationReceived(PBNotification notification)
            {
                if (notification.getType() == Type.CONFLICT_COUNT) {
                    GUI.get().safeAsyncExec(LstConflicts.this, LstConflicts.this::reload);
                }
            }

            @Override
            public void onNotificationChannelBroken()
            {

            }
        };

        UIGlobals.rnc().addListener(notificationListener);
        addDisposeListener(disposeEvent -> UIGlobals.rnc().removeListener(notificationListener));
    }

    public void setConflictEventListener(IConflictEventListener listener)
    {
        checkState(GUI.get().isUIThread());
        _listener = listener;
    }

    private void notifyStatusChanged(boolean isBusy)
    {
        checkState(GUI.get().isUIThread());
        if (_listener != null) _listener.onStatusChanged(isBusy);
    }

    private void notifyVersionDataStale()
    {
        checkState(GUI.get().isUIThread());
        if (_listener != null) _listener.onVersionDataStale();
    }

    public void addPostSelectionChangedListener(ISelectionChangedListener listener)
    {
        checkState(GUI.get().isUIThread());
        _viewer.addPostSelectionChangedListener(listener);
    }

    /**
     * @return null if nothing's selected.
     */
    public Conflict getSelectedConflictNullable()
    {
        checkState(GUI.get().isUIThread());
        return (Conflict)((IStructuredSelection)_viewer.getSelection()).getFirstElement();
    }

    public void reload()
    {
        checkState(GUI.get().isUIThread());
        notifyStatusChanged(true);
        _model.listConflicts(new GUIExecutor(_table), new FutureCallback<Collection<Conflict>>()
        {
            @Override
            public void onSuccess(Collection<Conflict> conflicts)
            {
                _viewer.setInput(conflicts);
                _viewer.refresh();

                notifyStatusChanged(false);
                notifyVersionDataStale();
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                ErrorMessages.show(getShell(), throwable,
                        "Sorry, we encountered an error while loading the list of files with " +
                                "conflicts.");

                notifyStatusChanged(false);
            }
        });
    }

    private class ConflictLabelProvider extends PathLabelProvider
    {
        public ConflictLabelProvider(TableViewerColumn column)
        {
            super(column);
        }

        @Override
        protected Path getPathNullable(Object element)
        {
            checkArgument(element instanceof Conflict);
            return ((Conflict) element)._path;
        }
    }
}
