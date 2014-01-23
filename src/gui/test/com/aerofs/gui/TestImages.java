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
        assertEquals("tray", Images.getTrayIconName(true, false, false, false, false, false));
        assertEquals("tray_off", Images.getTrayIconName(false, false, false, false, false, false));
        assertEquals("tray_n", Images.getTrayIconName(true, true, false, false, false, false));
        assertEquals("tray_sip", Images.getTrayIconName(true, false, true, false, false, false));
        assertEquals("tray_is", Images.getTrayIconName(true, false, false, true, false, false));
        assertEquals("tray@2x", Images.getTrayIconName(true, false, false, false, true, false));
        assertEquals("tray_win", Images.getTrayIconName(true, false, false, false, false, true));
        assertEquals("tray_off_n_sip_win@2x", Images.getTrayIconName(false, true, true, true, true, true));
    }
}
