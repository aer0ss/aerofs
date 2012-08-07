package com.aerofs.gui.transfers;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.Images;
import com.aerofs.lib.Util;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;

public class CompTransferStat extends Composite {

    private final Label _lblIn;
    private final Label _lblOut;

    private long _lastT;
    private long _lastIn;
    private long _lastOut;

    private boolean _inited;

    /**
     * Create the composite.
     * @param parent
     * @param style
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
        _lblIn.setText("9999.99 Mbps");

        CLabel label = new CLabel(this, SWT.NONE);
        label.setImage(Images.get(Images.ICON_ARROW_UP));
        _lblOut = new Label(this, SWT.NONE);
        _lblOut.setText("9999.99 Mbps");

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
        .addTp(PBTransport.newBuilder().setBytesIn(0).setBytesOut(0))
        .build();

    private void thdRefresh()
    {
        while (true) {
            if (isDisposed()) break;

            String strIn, strOut;

            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
            try {
                PBDumpStat data = ritual.dumpStats(TEMPLATE).getStats();
                long in = 0, out = 0;
                for (PBTransport tp : data.getTpList()) {
                    in += tp.getBytesIn();
                    out += tp.getBytesOut();
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
                Util.l().warn("can't refresh stat: " + Util.e(e));
                strIn = Util.formatBandwidth(0, 0);
                strOut = Util.formatBandwidth(0, 0);
            } finally {
                ritual.close();
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

            Util.sleepUninterruptable(GUIParam.STAT_UPDATE_INTERVAL);
        }
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }
}
