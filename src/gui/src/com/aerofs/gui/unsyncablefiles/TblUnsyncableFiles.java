/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.unsyncablefiles;

import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
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
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import java.util.Iterator;
import java.util.Map;

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
        colFilename.setLabelProvider(new FilenameProvider(new GC(_table), colFilename.getColumn()));
        colFilename.getColumn().setText("Filename");
        // refresh the whole table if the column width for filename changed because the filename is
        // shortened based on the column width
        colFilename.getColumn().addControlListener(new ControlAdapter()
        {
            @Override
            public void controlResized(ControlEvent e)
            {
                _tableViewer.refresh();
            }
        });

        TableViewerColumn colReason = new TableViewerColumn(_tableViewer, SWT.LEFT);
        colReason.setLabelProvider(new DetailProvider());
        colReason.getColumn().setText("Detail");

        TableColumnLayout layout = new TableColumnLayout();
        // golden ratio
        layout.setColumnData(colFilename.getColumn(), new ColumnWeightData(618, true));
        layout.setColumnData(colReason.getColumn(), new ColumnWeightData(382, true));
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

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        _tableViewer.addSelectionChangedListener(listener);
    }

    // also shortens the filename based on the column width
    private class FilenameProvider extends ColumnLabelProvider
    {
        private final GC _gc;
        private final TableColumn _column;
        private final Map<Program, Image> _iconCache;

        public FilenameProvider(GC gc, TableColumn column)
        {
            _gc = gc;
            _column = column;

            _iconCache = Maps.newHashMap();
            addDisposeListener(new DisposeListener()
            {
                @Override
                public void widgetDisposed(DisposeEvent disposeEvent)
                {
                    for (Image image : _iconCache.values()) image.dispose();
                    _iconCache.clear();
                }
            });
        }

        @Override
        public String getText(Object element)
        {
            checkArgument(element instanceof UnsyncableFile);
            String path = ((UnsyncableFile) element)._path.toStringRelative();
            String text = UIUtil.getPrintablePath(path);
            return GUIUtil.shortenText(_gc, text, _column, true);
        }

        @Override
        public Image getImage(Object element)
        {
            checkArgument(element instanceof UnsyncableFile);
            UnsyncableFile file = (UnsyncableFile) element;

            // this check is expensive because we have to build PB first #sigh
            if (UIUtil.isSystemFile(file._path.toPB())) return Images.get(Images.ICON_METADATA);
            else return Images.getFileIcon(file._path.last(), _iconCache);
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
