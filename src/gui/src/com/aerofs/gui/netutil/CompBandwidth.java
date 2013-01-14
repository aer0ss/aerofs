package com.aerofs.gui.netutil;

import com.aerofs.base.id.DID;
import com.aerofs.lib.Param;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Ritual.TransportFloodQueryReply;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.GridLayout;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.base.C;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.ui.UIParam;

import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;

public class CompBandwidth extends Composite {

    private final DID _did;
    private final Button _btnStart;
    private final CompBandwidthStatus _compDownloadStatus;
    private final CompBandwidthStatus _compUploadStatus;
    private final CompPing _compPing;
    private final String _sname;
    private boolean _stop = true;

    /**
     * Create the composite.
     * @param parent
     * @param style
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
                RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
                _compPing.suspend();
                try {
                    try {
                        thdRun(_compUploadStatus, true, ritual);
                    } catch (Exception e) {
                        _compUploadStatus.done(e, 0, 0);
                    }

                    try {
                        thdRun(_compDownloadStatus, false, ritual);
                    } catch (Exception e) {
                        _compDownloadStatus.done(e, 0, 0);
                    }

                } finally {
                    ritual.close();

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
        long lastRpc = 0;
        long timeoutStart = 0;
        long floodStart = 0;

        while (!isDisposed() && !_stop) {

            long now = System.currentTimeMillis();

            switch (state) {
            case INIT:
                comp.init();
                ritual.transportFlood(_did.toPB(), send, seqStart, seqEnd,
                        UIParam.TRANSPORT_FLOOD_DURATION, _sname);
                lastRpc = now;
                state = State.QUERY_START;
                timeoutStart = now;
                break;

            case QUERY_START:
                if (now - lastRpc > RPC_INTERVAL) {
                    rpcFloodQuery(ritual, seqStart, timeStart, bytesStart);
                    lastRpc = now;
                    if (timeStart.get() == Param.TRANSPORT_DIAGNOSIS_STATE_PENDING) {
                        if (now - timeoutStart > Cfg.timeout()) throw new ExTimeout();
                    } else {
                        state = State.QUERY_END;
                        comp.progress(0);
                        timeoutStart = now;
                        floodStart = now;
                    }
                }
                break;

            case QUERY_END:
                if (now - lastRpc > RPC_INTERVAL) {
                    rpcFloodQuery(ritual, seqEnd, timeEnd, bytesEnd);
                    lastRpc = now;
                    if (timeEnd.get() == Param.TRANSPORT_DIAGNOSIS_STATE_PENDING) {
                        if (now - timeoutStart > Cfg.timeout()) throw new ExTimeout();
                    } else {
                        long bytes = bytesEnd.get() - bytesStart.get();
                        long interval = timeEnd.get() - timeStart.get();
                        comp.done(null, bytes, interval);
                        Util.l(this).warn("flood " + _did.toStringFormal() +
                                " (" + (send ? "send" : "recv") + "): " +
                                bytes + " / " + interval + " = " +
                                Util.formatBandwidth(bytes, interval));
                        return;
                    }
                }

                long percent = (now - floodStart) * 100 / UIParam.TRANSPORT_FLOOD_DURATION;
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
