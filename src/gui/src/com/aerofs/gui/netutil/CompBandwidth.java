package com.aerofs.gui.netutil;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.base.id.DID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.proto.Ritual.TransportFloodQueryReply;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIParam;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.slf4j.Logger;

public class CompBandwidth extends Composite
{
    private static final Logger l = Loggers.getLogger(CompBandwidth.class);

    private final DID _did;
    private final Button _btnStart;
    private final CompBandwidthStatus _compDownloadStatus;
    private final CompBandwidthStatus _compUploadStatus;
    private final CompPing _compPing;
    private final String _sname;
    private boolean _stop = true;

    /**
     * Create the composite.
     */
    public CompBandwidth(Composite parent, CompPing compPing, int style,
            DID did, String sname)
    {
        super(parent, style);

        _compPing = compPing;
        _did = did;
        _sname = sname;

        GridLayout gridLayout = new GridLayout(4, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = GUIParam.MARGIN;
        setLayout(gridLayout);

        label = new Label(this, SWT.NONE);
        label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));

        Label lblCurrent = new Label(this, SWT.RIGHT);
        lblCurrent.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblCurrent.setText("Upload Speed:");

        _compUploadStatus = new CompBandwidthStatus(this, SWT.NONE);

        Label lblMax = new Label(this, SWT.NONE);
        lblMax.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);

        Label lblDuration = new Label(this, SWT.NONE);
        lblDuration.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblDuration.setText("Download Speed:");

        _compDownloadStatus = new CompBandwidthStatus(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);

        _btnStart = new Button(this, SWT.NONE);
        _btnStart.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (_stop) {
                    _stop = false;
                    _btnStart.setEnabled(false);
                    //_btnStart.setText("Stop");
                    start();
                } else {
                    // request to stop
                    _stop = true;
                    // the thread in start() will reenable the button right before
                    // it exits
                    _btnStart.setEnabled(false);
                }
            }
        });
        _btnStart.setText("    Test    ");
        new Label(this, SWT.NONE);
    }

    private void start()
    {
        Thread thd = new Thread() {
            @Override
            public void run()
            {
                _compPing.suspend();
                try {
                    try {
                        thdRun(_compUploadStatus, true, UIGlobals.ritual());
                    } catch (Exception e) {
                        _compUploadStatus.done(e, 0, 0);
                    }

                    try {
                        thdRun(_compDownloadStatus, false, UIGlobals.ritual());
                    } catch (Exception e) {
                        _compDownloadStatus.done(e, 0, 0);
                    }

                } finally {
                    // re-enable the start button
                    GUI.get().safeAsyncExec(CompBandwidth.this, new Runnable() {
                        @Override
                        public void run()
                        {
                            _stop = true;
                            _btnStart.setText("Start");
                            _btnStart.setEnabled(true);
                        }
                    });

                    _compPing.resume();
                }
            }
        };
        thd.setDaemon(true);
        thd.start();
    }

    private static enum State {
        INIT,
        QUERY_START,
        QUERY_END
    }

    private static final long RPC_INTERVAL = 1 * C.SEC;
    private final Label label;

    private void thdRun(CompBandwidthStatus comp, boolean send, RitualBlockingClient ritual)
        throws Exception
    {
        int seqStart = Util.rand().nextInt();
        int seqEnd = Util.rand().nextInt();

        OutArg<Long> timeStart = new OutArg<Long>();
        OutArg<Long> timeEnd = new OutArg<Long>();
        OutArg<Long> bytesStart = new OutArg<Long>();
        OutArg<Long> bytesEnd = new OutArg<Long>();

        State state = State.INIT;
        ElapsedTimer rpcTimer = new ElapsedTimer();
        ElapsedTimer timeoutTimer = new ElapsedTimer();
        ElapsedTimer floodTimer = new ElapsedTimer();

        while (!isDisposed() && !_stop) {

            switch (state) {
            case INIT:
                comp.init();
                ritual.transportFlood(_did.toPB(), send, seqStart, seqEnd,
                        UIParam.TRANSPORT_FLOOD_DURATION, _sname);
                rpcTimer.start();
                state = State.QUERY_START;
                timeoutTimer.start();
                break;

            case QUERY_START:
                if (rpcTimer.elapsed() > RPC_INTERVAL) {
                    rpcFloodQuery(ritual, seqStart, timeStart, bytesStart);
                    rpcTimer.restart();
                    if (timeStart.get() == LibParam.TRANSPORT_DIAGNOSIS_STATE_PENDING) {
                        if (timeoutTimer.elapsed() > Cfg.timeout()) throw new ExTimeout();
                    } else {
                        state = State.QUERY_END;
                        comp.progress(0);
                        timeoutTimer.restart();
                        floodTimer.restart();
                    }
                }
                break;

            case QUERY_END:
                if (rpcTimer.elapsed() > RPC_INTERVAL) {
                    rpcFloodQuery(ritual, seqEnd, timeEnd, bytesEnd);
                    rpcTimer.restart();
                    if (timeEnd.get() == LibParam.TRANSPORT_DIAGNOSIS_STATE_PENDING) {
                        if (timeoutTimer.elapsed() > Cfg.timeout()) throw new ExTimeout();
                    } else {
                        long bytes = bytesEnd.get() - bytesStart.get();
                        long interval = timeEnd.get() - timeStart.get();
                        comp.done(null, bytes, interval);
                        l.warn("flood " + _did.toStringFormal() +
                                " (" + (send ? "send" : "recv") + "): " +
                                bytes + " / " + interval + " = " +
                                Util.formatBandwidth(bytes, interval));
                        return;
                    }
                }

                long percent = (floodTimer.elapsed()) * 100 / UIParam.TRANSPORT_FLOOD_DURATION;
                if (percent >= 100) {
                    comp.collecting();
                } else {
                    comp.progress((int) percent);
                }
                break;

            default:
                assert false;
            }

            ThreadUtil.sleepUninterruptable(100);
        }

        // stop requested
        if (_stop) comp.reset();
    }

    private void rpcFloodQuery(RitualBlockingClient ritual, int seq, OutArg<Long> time,
            OutArg<Long> bytes) throws Exception
    {
        TransportFloodQueryReply reply = ritual.transportFloodQuery(_did.toPB(), seq);
        time.set(reply.getTime());
        bytes.set(reply.getBytes());
    }

}
