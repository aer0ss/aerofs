package com.aerofs.gui.netutil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

import com.aerofs.InternalDiagnostics;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.GridLayout;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.base.C;
import com.aerofs.lib.Util;
import com.aerofs.InternalDiagnostics.IPingCallback;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.UIParam;

import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import com.swtdesigner.SWTResourceManager;
import org.slf4j.Logger;

public class CompPing extends Composite
{
    private static final Logger l = Loggers.getLogger(CompPing.class);

    private static final Long[] TIMEOUTS =
        { 1 * C.SEC, 5 * C.SEC, 10 * C.SEC, 30 * C.SEC,
          1 * C.MIN, 5 * C.MIN, 10 * C.MIN};
    private static final int TIMEOUT_DEFAULT = 1;

    private final Label _lblElapsed;
    private final Label _lblCurrent;
    private final Label _lblLoss;
    private final Label _lblMax;
    private final Label _lblMedian;
    private final Label _lblMin;
    private final Label _lblInterval;

    private final DID _did;
    private long _timeout = TIMEOUTS[TIMEOUT_DEFAULT];
    private long _startTime;
    private boolean _suspend;

    /**
     * Create the composite.
     * @param parent
     * @param style
     */
    public CompPing(Composite parent, int style, DID did)
    {
        super(parent, style);

        _did = did;

        int widthHint = GUIUtil.getExtent(parent, "9.999999 secs").x;

        GridLayout gridLayout = new GridLayout(4, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = GUIParam.MARGIN;
        setLayout(gridLayout);

        Label lblCurrent = new Label(this, SWT.RIGHT);
        GridData gd_lblCurrent = new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1);
        gd_lblCurrent.widthHint = widthHint;   // to maintain balance against _lblMax's width hint
        lblCurrent.setLayoutData(gd_lblCurrent);
        lblCurrent.setText("Round-trip:");

        _lblCurrent = new Label(this, SWT.NONE);
        GridData gd__lblCurrent = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd__lblCurrent.widthHint = widthHint;
        _lblCurrent.setLayoutData(gd__lblCurrent);
        _lblCurrent.setText("\u221E");
        _lblCurrent.setFont(GUIUtil.makeBold(_lblCurrent.getFont()));

        Label lblMax = new Label(this, SWT.NONE);
        lblMax.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblMax.setText("Max:");

        _lblMax = new Label(this, SWT.NONE);
        GridData gd__lblMax = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd__lblMax.widthHint = widthHint;
        _lblMax.setLayoutData(gd__lblMax);
        _lblMax.setText("\u221E");

        Label lblLoss = new Label(this, SWT.NONE);
        lblLoss.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
        lblLoss.setText("Packet Loss:");

        _lblLoss = new Label(this, SWT.NONE);
        _lblLoss.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        _lblLoss.setText("0% of 0");

        Label lblAverage = new Label(this, SWT.NONE);
        lblAverage.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblAverage.setText("Median:");

        _lblMedian = new Label(this, SWT.NONE);
        _lblMedian.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _lblMedian.setText("\u221E");

        Label lblDuration = new Label(this, SWT.NONE);
        lblDuration.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
        lblDuration.setText("Elapsed:");

        _lblElapsed = new Label(this, SWT.NONE);
        _lblElapsed.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        _lblElapsed.setText(Util.formatRelativeTime(0));

        Label lblMin = new Label(this, SWT.NONE);
        lblMin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblMin.setText("Min:");

        _lblMin = new Label(this, SWT.NONE);
        _lblMin.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _lblMin.setText("\u221E");
        new Label(this, SWT.NONE);

        _lblPaused = new Label(this, SWT.NONE);
        _lblPaused.setForeground(SWTResourceManager.getColor(SWT.COLOR_RED));
        _lblPaused.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 2, 1));
        _lblPaused.setText("Paused");
        _lblPaused.setVisible(false);

        _lblTimeWindow2 = new Label(this, SWT.NONE);
        _lblTimeWindow2.setText("* last " + Util.formatRelativeTime(UIParam.TRANSPORT_PING_SAMPLE_TIME_WINDOW));
        _lblTimeWindow2.setVisible(false);

        Label lblInterval = new Label(this, SWT.NONE);
        lblInterval.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
        lblInterval.setText("Time Out:");

        final Scale scale = new Scale(this, SWT.NONE);
        GridData gd_scale = new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1);
        gd_scale.widthHint = 90;
        scale.setLayoutData(gd_scale);
        scale.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                _timeout = TIMEOUTS[scale.getSelection()];
                _lblInterval.setText(Util.formatRelativeTime(_timeout));
            }
        });

        scale.setPageIncrement(1);
        scale.setIncrement(1);
        scale.setMaximum(TIMEOUTS.length - 1);
        scale.setMinimum(0);
        scale.setSelection(TIMEOUT_DEFAULT);

        _lblInterval = new Label(this, SWT.NONE);
        _lblInterval.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        _lblInterval.setText(Util.formatRelativeTime(_timeout));

        _startTime = System.currentTimeMillis();

        Thread thd = new Thread() {
            @Override
            public void run()
            {
                RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
                try {
                    InternalDiagnostics.ping(ritual, _did, true, new IPingCallback()
                    {
                        @Override
                        public boolean toStop()
                        {
                            return isDisposed();
                        }

                        @Override
                        public boolean toSuspend()
                        {
                            return _suspend;
                        }

                        @Override
                        public long getTimeout()
                        {
                            return _timeout;
                        }

                        @Override
                        public void update(boolean offline, Long rtt, int samples)
                        {
                            updateUI(null, offline, rtt, samples);
                        }
                    });
                } catch (Exception e) {
                    updateUI(e, false, null, _samples);
                } finally {
                    ritual.close();
                    logStat();
                }
            }
        };
        thd.setDaemon(true);
        thd.start();
    }

    /**
     * it may be called in any thread
     */
    void suspend()
    {
        _suspend = true;
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run()
            {
                _lblPaused.setVisible(true);
            }
        });
    }

    /**
     * it may be called in any thread
     */
    void resume()
    {
        _suspend = false;
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run()
            {
                _lblPaused.setVisible(false);
            }
        });
    }

    void logStat()
    {
        l.warn("ping " + _did.toStringFormal() + ":" +
                " elapsed " + Util.format(System.currentTimeMillis() - _startTime) +
                " samples " + _samples + " loss " + _loss +
                " max " + Util.format(_max) +
                " min " + Util.format(_min) +
                " median " + Util.format(_median));
    }

    private int _samples;
    private long _max = Long.MAX_VALUE;
    private long _min = Long.MAX_VALUE;
    private long _median = Long.MAX_VALUE;
    private int _loss;

    private static class RTTEntry {
        long _rtt;
        long _time;
    }

    // maximum list size is PING_SAMPLE_TIME_WINDOW / PING_INTERVAL_MIN
    private final LinkedList<RTTEntry> _rtts = new LinkedList<RTTEntry>();
    private boolean _rttsTruncated = false;

    private final Comparator<RTTEntry> _comp = new Comparator<RTTEntry>() {
            @Override
            public int compare(RTTEntry arg0, RTTEntry arg1)
            {
                return (int) (arg0._rtt - arg1._rtt);
            }
        };
    private Label _lblTimeWindow2;
    private Label _lblPaused;

    /**
     * @param rtt must be non-null and positive. MAX_VALUE if timed out
     */
    private void updateStat(long now, boolean offline, long rtt)
    {
        if (offline) {
            _loss++;
            return;
        }

        assert rtt >= 0;

        if (rtt < _min) _min = rtt;

        if (rtt == Long.MAX_VALUE) {
            _loss++;

        } else {
            if (_max == Long.MAX_VALUE || rtt > _max) _max = rtt;

            RTTEntry en = new RTTEntry();
            en._rtt = rtt;
            en._time = now;
            _rtts.addLast(en);
        }

        Iterator<RTTEntry> iter = _rtts.iterator();
        while (iter.hasNext()) {
            RTTEntry en = iter.next();
            if (now - en._time > UIParam.TRANSPORT_PING_SAMPLE_TIME_WINDOW) {
                iter.remove();
                _rttsTruncated = true;
            } else {
                break;
            }
        }

        RTTEntry arr[] = new RTTEntry[_rtts.size()];
        _rtts.toArray(arr);
        Arrays.sort(arr, _comp);

        if (arr.length == 0) {
            _median = Long.MAX_VALUE;
        } else {
            int half = arr.length / 2;
            _median = arr.length % 2 == 1 ? arr[half]._rtt :
                (arr[half - 1]._rtt + arr[half]._rtt) / 2;
        }
    }

    private void updateUI(final Exception e, final boolean offline,
            final Long rtt, int samples)
    {
        _samples = samples;

        if (isDisposed()) return;

        final long now = System.currentTimeMillis();

        if (e == null) {
            if (offline) updateStat(now, offline, 0);
            else if (rtt != null) updateStat(now, offline, rtt);
        }

        // syncExec instead of asyncExec to avoid overloading the GUI
        getDisplay().syncExec(new Runnable() {
            @Override
            public void run()
            {
                if (isDisposed()) return;

                if (e != null) {
                    _lblCurrent.setText("Error");
                    SVClient.logSendDefectAsync(true, "can't ping", e);

                } else if (offline) {
                    _lblCurrent.setText("Offline");

                } else if (rtt == null) {
                    // don't update stat

                } else {
                    _lblCurrent.setText(Util.formatRelativeTimeHiRes(rtt));

                    _lblMax.setText(Util.formatRelativeTimeHiRes(_max));

                    _lblMin.setText(Util.formatRelativeTimeHiRes(_min));

                    _lblMedian.setText(Util.formatRelativeTimeHiRes(_median) +
                            (!_rttsTruncated ? "" : " *"));

                    if (_rttsTruncated) _lblTimeWindow2.setVisible(true);
                }

                _lblLoss.setText((_samples == 0 ? "0" : (_loss * 100 / _samples)) +
                        "% of " + _samples);
                _lblElapsed.setText(Util.formatRelativeTime(now - _startTime));
            }
        });
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

}
