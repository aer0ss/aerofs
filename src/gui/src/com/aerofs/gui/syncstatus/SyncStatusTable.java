/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.syncstatus;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.Images;
import com.aerofs.gui.syncstatus.SyncStatusModel.ExServerUnavialable;
import com.aerofs.gui.syncstatus.SyncStatusModel.SyncStatusEntry;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.slf4j.Logger;

import java.util.Collection;

public class SyncStatusTable extends Composite
{
    private final SyncStatusModel _model =
            new SyncStatusModel(new CfgLocalUser(), UIGlobals.ritualClientProvider());

    private final TableViewer   _viewer;
    private final Table         _table;

    public SyncStatusTable(Composite parent)
    {
        super(parent, SWT.NONE);

        _viewer = new TableViewer(this, SWT.BORDER);
        _table = _viewer.getTable();

        TableViewerColumn column = new TableViewerColumn(_viewer, SWT.NONE);

        TableColumnLayout layout = new TableColumnLayout();
        layout.setColumnData(column.getColumn(), new ColumnWeightData(1));
        setLayout(layout);
    }

    // N.B. this method is non-reentrant when the service is unavailable.
    public void loadFromPath(Path path)
    {
        _model.getSyncStatusEntries(path, new GUIExecutor(_table),
                new FutureCallback<Collection<SyncStatusEntry>>()
                {
                    @Override
                    public void onSuccess(Collection<SyncStatusEntry> input)
                    {
                        _viewer.setContentProvider(new SyncStatusContentProvider());
                        _viewer.setComparator(new SyncStatusComparator());
                        _viewer.setLabelProvider(new SyncStatusLabelProvider(_table));
                        _viewer.setInput(input);

                        ColumnViewerToolTipSupport.enableFor(_viewer);
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        if (throwable instanceof ExServerUnavialable) {
                            Logger l = Loggers.getLogger(SyncStatusTable.class);
                            l.error("sync stat unavailable.", throwable);

                            onServiceUnavailable();
                            return;
                        }

                        String message = "Sorry, we encountered an error while retrieving " +
                                "the Sync Status for this file.";

                        ErrorMessages.show(getShell(), throwable, message);
                    }
                });
    }

    private void onServiceUnavailable()
    {
        String message = "Sorry, the Sync Status for this file is unavailable at this moment. " +
                "Please try again later.";
        showMessage(Images.get(Images.ICON_ERROR), message);
    }

    private void showMessage(Image image, String text)
    {
        for (Control c : getChildren()) if (!c.isDisposed()) c.dispose();

        Group content = new Group(this, SWT.BORDER);

        Label icon = new Label(content, SWT.NONE);
        icon.setImage(image);

        Label label = new Label(content, SWT.WRAP);
        label.setText(text);

        GridLayout contentLayout = new GridLayout(2, false);
        contentLayout.marginWidth = 0;
        contentLayout.marginHeight = 0;
        content.setLayout(contentLayout);

        icon.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        FillLayout layout = new FillLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        setLayout(layout);

        layout();
    }
}
