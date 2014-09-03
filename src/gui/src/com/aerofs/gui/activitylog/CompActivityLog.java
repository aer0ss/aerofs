package com.aerofs.gui.activitylog;

import com.aerofs.base.Loggers;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.GetActivitiesReply;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.slf4j.Logger;

import javax.annotation.Nullable;

public class CompActivityLog extends Composite
{
    private static final Logger l = Loggers.getLogger(CompActivityLog.class);

    private static final int MAX_RESULTS_MIN = 20;
    private static final int MAX_RESULTS_MAX = 200;

    private final TableViewer _tv;

    private final TableColumn _tcTime;
    private final TableColumn _tcMsg;

    private boolean _recalcing;
    private int _maxResult = MAX_RESULTS_MIN;
    private Long _pageToken;
    private final Button _btnMore;
    private final CompSpin _compSpin;
    private final Label _lblStatus;
    private final Button _btnView;

    /**
     * @param initialSelection the index of the item to be selected when the activity log is
     * initially populated. null if nothing to select.
     */
    public CompActivityLog(Composite parent, final DlgActivityLog dlg, Integer initialSelection)
    {
        super(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = GUIParam.MARGIN;
        setLayout(gridLayout);
        _tv = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION);
        final Table t = _tv.getTable();
        GridData gd_t = new GridData(SWT.FILL, SWT.FILL, false, true, 3, 1);
        gd_t.widthHint = 600;
        gd_t.heightHint = 250;
        t.setLayoutData(gd_t);
        t.setBackground(getShell().getDisplay().getSystemColor(SWT.COLOR_WHITE));
        t.setHeaderVisible(true);

        if (Cfg.storageType() == StorageType.LINKED) {
            t.addSelectionListener(new SelectionListener() {
                @Override
                public void widgetSelected(SelectionEvent arg0)
                {
                    PBActivity a = getSelectedActivty();
                    _btnView.setEnabled(a != null && a.hasPath());
                }

                @Override
                public void widgetDefaultSelected(SelectionEvent arg0)
                {
                    PBActivity a = getSelectedActivty();
                    if (a != null && a.hasPath()) revealFile(a.getPath());
                }
            });
        }

        ////////
        // add columns

        TableViewerColumn tvcMsg = new TableViewerColumn(_tv, SWT.NONE);
        tvcMsg.setLabelProvider(new MsgLabelProvider());
        _tcMsg = tvcMsg.getColumn();
        _tcMsg.setText("Activity");

        TableViewerColumn tvcSubject = new TableViewerColumn(_tv, SWT.RIGHT);
        tvcSubject.setLabelProvider(new TimeLabelProvider());
        _tcTime = tvcSubject.getColumn();
        _tcTime.setText("Received at");

        ////////
        // table-wise operations

        t.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e)
            {
                recalcColumnWidths();
            }
        });

        _compSpin = new CompSpin(this, SWT.NONE);

        _lblStatus = new Label(this, SWT.NONE);
        _lblStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _lblStatus.setText("New Label");

        Composite composite = new Composite(this, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        FillLayout fl_composite = new FillLayout(SWT.HORIZONTAL);
        fl_composite.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(fl_composite);

        _btnMore = GUIUtil.createButton(composite, SWT.NONE);
        _btnMore.setText("Show More");
        _btnMore.setVisible(false);
        _btnMore.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                loadMoreAsync(null);
            }
        });

        if (Cfg.storageType() == StorageType.LINKED) {
            _btnView = GUIUtil.createButton(composite, SWT.NONE);
            _btnView.setText("Reveal File");
            _btnView.setEnabled(false);
            _btnView.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0)
                {
                    PBActivity a = getSelectedActivty();
                    if (a == null || !a.hasPath()) return;
                    revealFile(a.getPath());
                }
            });
        } else {
            _btnView = null;
        }

        Button btnClose = GUIUtil.createButton(composite, SWT.NONE);
        btnClose.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                dlg.closeDialog();
            }
        });
        btnClose.setText(IDialogConstants.CLOSE_LABEL);

        loadMoreAsync(initialSelection);
    }

    private void revealFile(PBPath path)
    {
        String str = UIUtil.absPathNullable(Path.fromPB(path));
        if (str != null) OSUtil.get().showInFolder(str);
    }

    private @Nullable PBActivity getSelectedActivty()
    {
        IStructuredSelection sel = ((IStructuredSelection) _tv.getSelection());
        Object o = sel.getFirstElement();
        if (o == null || !(o instanceof PBActivity)) return null;

        return (PBActivity) o;
    }

    private void loadMoreAsync(final Integer selection)
    {
        assert UI.get().isUIThread();
        _compSpin.start();
        _lblStatus.setText("");

        ThreadUtil.startDaemonThread("gui-actv", new Runnable()
        {
            @Override
            public void run()
            {
                thdLoadMoreAsync(selection);
            }
        });
    }

    private void recalcColumnWidths()
    {
        // this is to prevent an infinite recursion bug found in production
        if (_recalcing) return;

        _recalcing = true;
        try {
            _tcTime.pack();
            Table t = _tv.getTable();
            ScrollBar sb = t.getVerticalBar();
            int scroll = OSUtil.isOSX() ? 1 : 0;
            scroll += sb != null && sb.isVisible() ? sb.getSize().x : 0;
            int width = t.getBounds().width - 2 * t.getBorderWidth() - scroll - _tcTime.getWidth();
            _tcMsg.setWidth(width);
        } finally {
            _recalcing = false;
        }
    }

    /**
     * This method can be called in either UI or non-UI threads
     */
    private void thdLoadMoreAsync(Integer selection)
    {
        Object[] elems;
        boolean hasUnresolved;
        try {
            GetActivitiesReply reply = UIGlobals.ritual().getActivities(false, _maxResult, _pageToken);
            elems = reply.getActivityList().toArray();
            hasUnresolved = reply.getHasUnresolvedDevices();
            _pageToken = reply.hasPageToken() ? reply.getPageToken() : null;
            _maxResult = Math.min(_maxResult * 2, MAX_RESULTS_MAX);
        } catch (Exception e) {
            l.warn(Util.e(e));
            elems = new Object[] { e };
            hasUnresolved = false;
            selection = null;
        }

        final Object[] elemsFinal = elems;
        final boolean hasUnresolvedFinal = hasUnresolved;
        final Integer selectionFinal = selection;
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run()
            {
                _tv.add(elemsFinal);
                recalcColumnWidths();

                if (selectionFinal != null) {
                    _tv.getTable().select(selectionFinal);
                }

                if (_pageToken == null) {
                    _btnMore.setEnabled(false);
                    _btnMore.setText("No More");
                } else {
                    _btnMore.setEnabled(true);
                    _btnMore.setVisible(true);
                }

                if (hasUnresolvedFinal) {
                    _compSpin.warning();
                    String msg = S.FAILED_FOR_ACCURACY;
                    _lblStatus.setText(msg);
                    if (_lblStatus.getBounds().width < GUIUtil.getExtent(getShell(), msg).x) {
                        getParent().pack();
                    }
                } else {
                    _compSpin.stop();
                }
            }
        });
    }
}
