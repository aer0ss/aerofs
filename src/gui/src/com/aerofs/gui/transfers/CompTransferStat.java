package com.aerofs.gui.transfers;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.Images;
import com.aerofs.lib.Util;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Ritual.GetTransferStatsReply;
import com.aerofs.ui.UIGlobals;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;

import static com.aerofs.gui.GUIUtil.createLabel;

public class CompTransferStat extends Composite
{
    private static final Logger l = Loggers.getLogger(CompTransferStat.class);

    private final Label _lblIn;
    private final Label _lblOut;

    private long _lastUp;
    private long _lastIn;
    private long _lastOut;

    private boolean _inited;

    /**
     * Create the composite.
     */
    public CompTransferStat(Composite parent, int style)
    {
        super(parent, style);
        GridLayout gridLayout = new GridLayout(6, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        setLayout(gridLayout);

        _lblIn = addBandwidthLabel_(this, Images.ICON_ARROW_DOWN);
        _lblOut = addBandwidthLabel_(this, Images.ICON_ARROW_UP);

        addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent arg0)
            {
                if (!_inited) {
                    _lblIn.setText(Util.formatBandwidth(0, 0));
                    _lblOut.setText(Util.formatBandwidth(0, 0));
                }

                removePaintListener(this);
            }
        });

        GUI.get().timerExec(0, _scheduleRefresh);
    }

    private static Label addBandwidthLabel_(Composite parent, String img)
    {
        CLabel icon = new CLabel(parent, SWT.NONE);
        icon.setImage(Images.get(img));
        Label lbl = createLabel(parent, SWT.NONE);
        // N.B. the label is initialized to a really long text so the initial layout
        //   allocates sufficient space for the values we are going to display.
        // The advantage of this approach is that we don't need to redo layout every
        //   time the text changes, which can get expensive since we do this often.
        // The disadvantage is that if we ever change the range of text we are going
        //   to display, this will need to change as well.
        lbl.setText("99999.99 bytes/s");
        return lbl;
    }

    private final Runnable _scheduleRefresh = new Runnable() {
        @Override
        public void run()
        {
            Futures.addCallback(UIGlobals.ritualNonBlocking().getTransferStats(),
                    new FutureCallback<GetTransferStatsReply>() {
                        @Override
                        public void onSuccess(final GetTransferStatsReply reply)
                        {
                            GUI.get().safeAsyncExec(CompTransferStat.this, new Runnable() {
                                @Override
                                public void run()
                                {
                                    refresh(reply);
                                }
                            });
                        }

                        @Override
                        public void onFailure(Throwable e)
                        {
                            l.warn("can't refresh stat: ",
                                    LogUtil.suppress(e, ClosedChannelException.class));
                            GUI.get().safeAsyncExec(CompTransferStat.this, new Runnable() {
                                @Override
                                public void run()
                                {
                                    refresh(0, 0, 0);
                                }
                            });
                        }
                    });
        }
    };

    private void refresh(GetTransferStatsReply data)
    {
        // if the daemon is restarted while transfers were ongoing, the first batch
        // of values will yield negative deltas, hence the use of Math.max
        long deltaIn = Math.max(0, data.getBytesIn() - _lastIn);
        long deltaOut = Math.max(0, data.getBytesOut() - _lastOut);
        long deltaT = Math.max(0, data.getUpTime() - _lastUp);

        _lastIn = data.getBytesIn();
        _lastOut = data.getBytesOut();
        _lastUp = data.getUpTime();

        refresh(deltaIn, deltaOut, deltaT);
    }

    private void refresh(long deltaIn, long deltaOut, long deltaT)
    {
        _lblIn.setText(Util.formatBandwidth(deltaIn, deltaT));
        _lblOut.setText(Util.formatBandwidth(deltaOut, deltaT));
        _inited = true;
        GUI.get().timerExec(GUIParam.STAT_UPDATE_INTERVAL, _scheduleRefresh);
    }
}
