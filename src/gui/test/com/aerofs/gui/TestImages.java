/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestImages
{
    @Test
    public void shouldProduceCorrectTrayIconName()
    {
        assertEquals("tray_oos", Images.getTrayIconName(true, false, false, false, false, false));
        assertEquals("tray_off_oos", Images.getTrayIconName(false, false, false, false, false, false));
        assertEquals("tray_n_oos", Images.getTrayIconName(true, true, false, false, false, false));
        assertEquals("tray_sip", Images.getTrayIconName(true, false, true, false, false, false));
        assertEquals("tray_is", Images.getTrayIconName(true, false, false, true, false, false));
        assertEquals("tray_oos@2x", Images.getTrayIconName(true, false, false, false, true, false));
        assertEquals("tray_oos_win", Images.getTrayIconName(true, false, false, false, false, true));
        assertEquals("tray_off_n_sip_win@2x", Images.getTrayIconName(false, true, true, true, true, true));
    }
}
