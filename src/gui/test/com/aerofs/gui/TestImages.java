/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import org.junit.Test;

import static org.testng.Assert.assertEquals;

public class TestImages
{
    @Test
    public void shouldProduceCorrectTrayIconName()
    {
        assertEquals(Images.getTrayIconName(true, false, 0), "tray0");
        assertEquals(Images.getTrayIconName(true, false, 5), "tray5");
        assertEquals(Images.getTrayIconName(true, false, 15), "tray1");
        assertEquals(Images.getTrayIconName(false, true, 7), "trayn0grey");
        assertEquals(Images.getTrayIconName(false, false, 7), "tray0grey");
        assertEquals(Images.getTrayIconName(true, true, 7), "trayn7");
    }
}
