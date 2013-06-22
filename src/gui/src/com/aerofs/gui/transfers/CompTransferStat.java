package com.aerofs.gui.transfers;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.Images;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.ui.UIGlobals;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.slf4j.Logger;

public class CompTransferStat extends Composite
{
    private static final Logger l = Loggers.getLogger(CompTransferStat.class);

    private final Label _lblIn;
    private final Label _lblOut;

    private long _lastT;
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

        CLabel label_1 = new CLabel(this, SWT.NONE);
        label_1.setImage(Images.get(Images.ICON_ARROW_DOWN));
        _lblIn = new Label(this, SWT.NONE);
        // N.B. the label is initialized to a really long text so the initial layout
        //   allocates sufficient space for the values we are going to display.
        // The advantage of this approach is that we don't need to redo layout every
        //   time the text changes, which can get expensive since we do this often.
        // The disadvantage is that if we ever change the range of text we are going
        //   to display, this will need to change as well.
        _lblIn.setText("99999.99 bytes/s");

        CLabel label = new CLabel(this, SWT.NONE);
        label.setImage(Images.get(Images.ICON_ARROW_UP));
        _lblOut = new Label(this, SWT.NONE);
        // N.B. the label is initialized to a really long text so the initial layout
        //   allocates sufficient space for the values we are going to display.
        // The advantage of this approach is that we don't need to redo layout every
        //   time the text changes, which can get expensive since we do this often.
        // The disadvantage is that if we ever change the range of text we are going
        //   to display, this will need to change as well.
        _lblOut.setText("99999.99 bytes/s");

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

        new Thread(new Runnable() {
            @Override
            public void run()
            {
                thdRefresh();
            }
        }, "refresh-stat").start();
    }

    private final PBDumpStat TEMPLATE = PBDumpStat.newBuilder()
        .setUpTime(0)
        .addTransport(PBTransport.newBuilder().setBytesIn(0).setBytesOut(0))
        .build();

    private void thdRefresh()
    {
        while (true) {
            if (isDisposed()) break;

            String strIn, strOut;

            try {
                PBDumpStat data = UIGlobals.ritual().dumpStats(TEMPLATE).getStats();
                long in = 0, out = 0;
                for (PBTransport transport : data.getTransportList()) {
                    in += transport.getBytesIn();
                    out += transport.getBytesOut();
                }

                if (_lastT == 0) _lastT = data.getUpTime();

                long now = System.currentTimeMillis();
                long deltaIn = in - _lastIn;
                long deltaOut = out - _lastOut;
                long deltaT = now - _lastT;

                _lastIn = in;
                _lastOut = out;
                _lastT = now;

                strIn = Util.formatBandwidth(deltaIn, deltaT);
                strOut = Util.formatBandwidth(deltaOut, deltaT);

            } catch (Exception e) {
                l.warn("can't refresh stat: " + Util.e(e));
                strIn = Util.formatBandwidth(0, 0);
                strOut = Util.formatBandwidth(0, 0);
            }

            final String strInFinal = strIn;
            final String strOutFinal = strOut;

            if (isDisposed()) break;
            GUI.get().safeExec(this, new Runnable() {
                @Override
                public void run()
                {
                    _lblIn.setText(strInFinal);
                    _lblOut.setText(strOutFinal);
                    _inited = true;
                }
            });

            ThreadUtil.sleepUninterruptable(GUIParam.STAT_UPDATE_INTERVAL);
        }
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }
}
