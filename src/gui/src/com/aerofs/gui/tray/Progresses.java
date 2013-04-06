package com.aerofs.gui.tray;

import java.util.ArrayList;
import java.util.TreeMap;

import com.aerofs.base.Loggers;
import com.aerofs.labeling.L;
import org.slf4j.Logger;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.UI;
import com.aerofs.ui.IUI.MessageType;

public class Progresses {
    private static final Logger l = Loggers.getLogger(Progresses.class);

    private static final String TOOLTIP_PREFIX =
            (L.isStaging() ? "STAGING " : "") + L.product();
    private static final String DEFAULT_TOOLTIP =
            TOOLTIP_PREFIX + " " + Cfg.ver() +
            (OSUtil.isOSX() ? "" : "\nDouble click to open " + L.product() + " folder");

    private final TreeMap<Integer, Progress> _progs =
        new TreeMap<Integer, Progress>();

    private int _lastProgressID;
    private final TrayIcon _ti;

    Progresses(SystemTray st)
    {
        _ti = st.getIcon();

        _ti.setToolTipText(DEFAULT_TOOLTIP);
    }

    public void removeProgress(Object obj)
    {
        Progress prog = (Progress) obj;
        l.debug("remove progress: " + prog.getTooltip());

        if (_ti.isDisposed()) return;

        boolean last;
        if (!_progs.isEmpty()) {
            last = _progs.lastKey().equals(prog.getId());
            _progs.remove(prog.getId());
        } else {
            last = false;   // the value doesn't matter
        }

        if (_progs.isEmpty()) {
            _ti.setToolTipText(DEFAULT_TOOLTIP);
            _ti.setSpin(false);

        } else if (last) {
            setProgressToolTipText(_progs.lastEntry().getValue());
            _ti.setSpin(true);
        }
    }

    public void removeAllProgresses()
    {
        // make a copy to avoid concurrent modification error
        for (Progress prog : new ArrayList<Progress>(_progs.values())) {
            removeProgress(prog);
        }
    }

    private void setProgressToolTipText(Progress prog)
    {
        _ti.setToolTipText(TOOLTIP_PREFIX + " | " + prog.getTooltip());
    }

    /**
     * All progresses will be destroyed next time daemon crashes.
     * @param msg optional message shown as tooltip. may be null
     * @param notify whether to show a balloon with the message on it
     *
     * @return a Progress object which must be passed into removeProgress later on
     */
    public Object addProgress(String msg, boolean notify)
    {
        Progress prog = new Progress(_lastProgressID++, msg + "...");

        if (_ti.isDisposed()) return prog;

        _progs.put(prog.getId(), prog);

        setProgressToolTipText(prog);
        _ti.setSpin(true);

        if (notify) UI.get().notify(MessageType.INFO, msg + "...");

        return prog;
    }
}
