package com.aerofs.gui.tray;

import java.util.TreeMap;

import com.aerofs.base.Loggers;
import com.aerofs.labeling.L;
import com.aerofs.ui.UI;
import org.slf4j.Logger;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;

public class Progresses {
    private static final Logger l = Loggers.getLogger(Progresses.class);

    private static final String TOOLTIP_PREFIX = L.product();
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
        l.debug("remove progress: " + prog._tooltip);

        if (_ti.isDisposed()) return;

        boolean last;
        if (!_progs.isEmpty()) {
            last = _progs.lastKey().equals(prog._id);
            _progs.remove(prog._id);
        } else {
            last = false;   // the value doesn't matter
        }

        if (_progs.isEmpty()) {
            _ti.setToolTipText(DEFAULT_TOOLTIP);
        } else if (last) {
            setProgressToolTipText(_progs.lastEntry().getValue());
        }
    }

    public void removeAllProgresses()
    {
        _progs.clear();
    }

    private void setProgressToolTipText(Progress prog)
    {
        _ti.setToolTipText(TOOLTIP_PREFIX + " | " + prog._tooltip);
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

        _progs.put(prog._id, prog);

        setProgressToolTipText(prog);

        if (notify) UI.get().notify(MessageType.INFO, msg + "...");

        return prog;
    }

    private static class Progress
    {
        public final int _id;
        public final String _tooltip;

        public Progress(int id, String tooltip)
        {
            _id = id;
            _tooltip = tooltip;
        }
    }
}
