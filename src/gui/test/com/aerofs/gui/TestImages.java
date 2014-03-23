/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import org.junit.Test;

import static com.aerofs.gui.Images.getTrayIconName;
import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.IN_SYNC;
import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.OUT_OF_SYNC;
import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.UNKNOWN;
import static org.junit.Assert.assertEquals;

public class TestImages
{
    @Test
    public void shouldProduceCorrectTrayIconName()
    {
        assertEquals("tray", getTrayIconName(true, false, false, false, OUT_OF_SYNC, false));
        assertEquals("tray_off", getTrayIconName(false, false, false, false, OUT_OF_SYNC, false));
        assertEquals("tray_n", getTrayIconName(true, true, false, false, OUT_OF_SYNC, false));
        assertEquals("tray_sip", getTrayIconName(true, false, true, false, OUT_OF_SYNC, false));
        assertEquals("tray_sip", getTrayIconName(true, false, true, true, IN_SYNC, false));
        assertEquals("tray", getTrayIconName(true, false, false, true, UNKNOWN, false));
        assertEquals("tray_is", getTrayIconName(true, false, false, true, IN_SYNC, false));
        assertEquals("tray_oos", getTrayIconName(true, false, false, true, OUT_OF_SYNC, false));
        assertEquals("tray_win", getTrayIconName(true, false, false, false, OUT_OF_SYNC, true));
        assertEquals("tray_off_n_sip_win", getTrayIconName(false, true, true, false, OUT_OF_SYNC,
                true));
    }
}
