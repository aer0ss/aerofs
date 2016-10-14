package com.aerofs.gui.transfers;

import com.aerofs.gui.TransferState.ITransferStateChangedListener;
import com.aerofs.lib.S;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.aerofs.gui.TransferState;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Table;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.TableColumn;

public class CompTransfersTable extends Composite
{
    static final int COL_PATH = 0;
    static final int COL_DEVICE = 1;
    static final int COL_TRANSPORT = 2;
    static final int COL_PROG = 3;

    private final TableViewer _tv;
    private final TransferLabelProvider _label;

    private TransferState _ts;
    private final ITransferStateChangedListener _listener;

    /**
     * Create the composite.
     * @param parent
     * @param style
     */
    public CompTransfersTable(Composite parent, int style)
    {
        super(parent, style);
        setLayout(new FillLayout(SWT.HORIZONTAL));

        _tv = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION);

        final Table table = _tv.getTable();
        table.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
        table.setHeaderVisible(true);

        final TableColumn tcPath = new TableColumn(table, SWT.NONE);
        tcPath.setMoveable(true);
        tcPath.setText(S.LBL_COL_PATH);
        tcPath.setWidth(192);
        tcPath.addListener(SWT.Resize, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                // update text shortening
                _tv.refresh();
            }
        });

        final TableColumn tcDevice = new TableColumn(table, SWT.NONE);
        tcDevice.setMoveable(true);
        tcDevice.setText(S.LBL_COL_DEVICE);
        tcDevice.setWidth(80);

        final TableColumn tcTransport = new TableColumn(table, SWT.NONE);
        tcTransport.setMoveable(true);
        tcTransport.setText(S.LBL_COL_TRANSPORT);
        tcTransport.setWidth(80);

        final TableColumn tcProgress = new TableColumn(table, SWT.NONE);
        tcProgress.setMoveable(true);
        tcProgress.setText(S.LBL_COL_PROGRESS);
        tcProgress.setWidth(80);

        _tv.setUseHashlookup(true);
        _tv.setContentProvider(new ContentProvider());
        _label = new TransferLabelProvider(new GC(table), tcPath);
        _tv.setLabelProvider(_label);

        _listener = new ITransferStateChangedListener()
        {
            @Override
            public void onTransferStateChanged(TransferState state)
            {
                if (!isDisposed()) _tv.refresh();
            }
        };

        addControlListener(new ControlAdapter()
        {
            @Override
            public void controlResized(ControlEvent e)
            {
                // without "-1" the horizontal scroll bar shows up sometimes
                int width = getBounds().width - 2 * table.getBorderWidth() - 1;
                tcPath.setWidth((int)(width * 0.4));
                tcDevice.setWidth((int)(width * 0.25));
                tcTransport.setWidth((int)(width * 0.1));
                tcProgress.setWidth((int)(width * 0.25));
            }
        });

        addDisposeListener(new DisposeListener()
        {
            @Override
            public void widgetDisposed(DisposeEvent disposeEvent)
            {
                if (_ts != null) _ts.removeListener(_listener);
            }
        });
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    public void showSOCID(boolean enable)
    {
        _label.showSOCID(enable);
        if (!isDisposed()) _tv.refresh();
    }

    public void showDID(boolean enable)
    {
        _label.showDID(enable);
        if (!isDisposed()) _tv.refresh();
    }

    public void setTransferState(TransferState ts)
    {
        if (_ts != null) _ts.removeListener(_listener);

        _ts = ts;
        if (!isDisposed()) _tv.setInput(_ts);

        if (ts != null) _ts.addListener(_listener);
    }
}
