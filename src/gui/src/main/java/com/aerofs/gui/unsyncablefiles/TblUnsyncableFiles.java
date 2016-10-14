/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.unsyncablefiles;

import com.aerofs.gui.common.PathLabelProvider;
import com.aerofs.lib.Path;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This composite acts as an intermediary to the table it contains, and it's used primarily so that
 * we can use the TableColumnLayout which manages the column widths automatically in a nice way.
 */
public class TblUnsyncableFiles extends Composite
{
    private final TableViewer   _tableViewer;
    private final Table         _table;

    public TblUnsyncableFiles(Composite parent)
    {
        super(parent, SWT.NONE);

        _tableViewer = new TableViewer(this, SWT.BORDER | SWT.MULTI);
        _tableViewer.setContentProvider(new ArrayContentProvider());

        _table = _tableViewer.getTable();
        _table.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
        _table.setLinesVisible(false);
        _table.setHeaderVisible(true);

        TableViewerColumn colFilename = new TableViewerColumn(_tableViewer, SWT.LEFT);
        colFilename.setLabelProvider(new FilenameProvider(colFilename));
        colFilename.getColumn().setText("Filename");

        TableViewerColumn colReason = new TableViewerColumn(_tableViewer, SWT.LEFT);
        colReason.setLabelProvider(new DetailProvider());
        colReason.getColumn().setText("Detail");

        TableColumnLayout layout = new TableColumnLayout();
        layout.setColumnData(colFilename.getColumn(), new ColumnWeightData(1, true));
        layout.setColumnData(colReason.getColumn(), new ColumnWeightData(1, true));
        setLayout(layout);
    }

    public void setFiles(ImmutableCollection<UnsyncableFile> files)
    {
        _tableViewer.setInput(files);
    }

    @SuppressWarnings("unchecked")
    public ImmutableList<UnsyncableFile> getSelectedFiles()
    {
        IStructuredSelection selection = (IStructuredSelection) _tableViewer.getSelection();
        return ImmutableList.copyOf((Iterator<UnsyncableFile>)selection.iterator()); // unchecked
    }

    public int getSelectedFilesCount()
    {
        IStructuredSelection selection = (IStructuredSelection) _tableViewer.getSelection();
        return selection.size();
    }

    public void addSelectionListener(SelectionListener listener)
    {
        _table.addSelectionListener(listener);
    }

    public void addPostSelectionChangedListener(ISelectionChangedListener listener)
    {
        _tableViewer.addPostSelectionChangedListener(listener);
    }

    private class FilenameProvider extends PathLabelProvider
    {
        public FilenameProvider(TableViewerColumn column)
        {
            super(column);
        }

        @Override
        protected Path getPathNullable(Object element)
        {
            checkArgument(element instanceof UnsyncableFile);
            return ((UnsyncableFile) element)._path;
        }
    }

    private class DetailProvider extends ColumnLabelProvider
    {
        @Override
        public String getText(Object element)
        {
            checkArgument(element instanceof UnsyncableFile);
            return ((UnsyncableFile) element)._reason;
        }
    }
}
