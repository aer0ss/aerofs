package com.aerofs.gui.transfers;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.aerofs.lib.id.DID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.ui.DelayedUIRunner;
import com.aerofs.ui.RitualNotificationClient;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.TransferState;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Table;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.TableColumn;

public class CompTransfersTable extends Composite
{
    private final TableViewer _tv;
    private final DelayedUIRunner _dr;
    private final TableColumn _tcPath;
    private final GC _gc;
    private final TransferState _ts = new TransferState(false);

    // the global RNC is not useful as we need to retrieve the full list of current transfers here.
    private final RitualNotificationClient _rnc = new RitualNotificationClient();

    private final IListener _l = new IListener() {
        @Override
        public void onNotificationReceived(PBNotification pb)
        {
            _ts.update(pb);
            _dr.schedule();
        }
    };

    static final int COL_PATH = 0;
    static final int COL_DEVICE = 1;
    static final int COL_PROG = 2;

    public String shortenPath(String text)
    {
        return GUIUtil.shortenText(_gc, text, _tcPath, true);
    }

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
        _tv.setUseHashlookup(true);
        _tv.setContentProvider(new ContentProvider());
        _tv.setLabelProvider(new LabelProvider(this));

        final Table table = _tv.getTable();
        table.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
        table.setHeaderVisible(true);

        _gc = new GC(table);

        _tcPath = new TableColumn(table, SWT.NONE);
        _tcPath.setMoveable(true);
        _tcPath.setText("File");
        _tcPath.setWidth(192);
        _tcPath.addListener(SWT.Resize, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                // update text shortening
                _tv.refresh();
            }
        });

        final TableColumn tcDevice = new TableColumn(table, SWT.NONE);
        tcDevice.setMoveable(true);
        tcDevice.setText("From/To");
        tcDevice.setWidth(80);

        final TableColumn tcProgress = new TableColumn(table, SWT.NONE);
        tcProgress.setMoveable(true);
        tcProgress.setText("Progress");
        tcProgress.setWidth(80);

        addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e)
            {
                // without "-1" the horizontal scroll bar shows up sometimes
                int width = getBounds().width - 2 * table.getBorderWidth() - 1;
                _tcPath.setWidth((int) (width * 0.5));
                tcDevice.setWidth((int) (width * 0.2));
                tcProgress.setWidth((int) (width * 0.3));
            }
        });

        _dr = new DelayedUIRunner(new Runnable() {
            @Override
            public void run()
            {
                if (!isDisposed()) _tv.refresh();
            }
        });

        getShell().addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent arg0)
            {
                _rnc.stop();
            }
        });

        // add the listener before starting the RNC to avoid missing events
        _rnc.addListener(_l);
        _rnc.start();

        _tv.setInput(_ts);
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    public String getDeviceString(DID did)
    {
        return did.toString();
    }
}
