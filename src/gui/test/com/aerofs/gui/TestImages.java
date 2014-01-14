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
        assertEquals(Images.getTrayIconName(true, true), "trayn0");
        assertEquals(Images.getTrayIconName(true, false), "tray0");
        assertEquals(Images.getTrayIconName(false, true), "trayn0grey");
        assertEquals(Images.getTrayIconName(false, false), "tray0grey");
    }
}
