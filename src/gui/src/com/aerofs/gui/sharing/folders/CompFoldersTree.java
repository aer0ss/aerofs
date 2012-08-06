package com.aerofs.gui.sharing.folders;

import javax.annotation.Nullable;

import com.aerofs.lib.Path;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

public class CompFoldersTree extends Composite
{
    final TreeViewer _v;
    final ContentProvider _cp = new ContentProvider();
    final IListener _l;

    static interface IListener
    {
        void selected(@Nullable Path path);

        void defaultSelected(@Nullable Path path);
    }

    public CompFoldersTree(Composite parent, IListener l)
    {
        super(parent, SWT.NONE);
        setLayout(new FillLayout(SWT.HORIZONTAL));

        _l = l;

        _v = new TreeViewer(this, SWT.BORDER);
        _v.setLabelProvider(new LabelProvider());
        _v.setContentProvider(_cp);
        _v.setSorter(new ViewerSorter());
        _v.setInput(_cp.getChildren(new Path()));

        _v.getTree().addSelectionListener(new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                _l.selected(getSelection());
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent ev)
            {
                _l.defaultSelected(getSelection());
            }
        });
    }

    private @Nullable Path getSelection()
    {
        IStructuredSelection sel = (IStructuredSelection) _v.getSelection();
        if (sel.size() == 1 && sel.getFirstElement() instanceof Path) {
            return (Path) sel.getFirstElement();
        } else {
            return null;
        }
    }
}
