package com.aerofs.lib;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestTick
{
    @Test
    public void itShouldAssignOddTickForAlias() throws Exception
    {
        Tick t = new Tick(10L);

        t = t.incAlias();
        assertEquals(11L, t.getLong());

        t = t.incAlias();
        assertEquals(13L, t.getLong());
    }

    @Test
    public void itShouldAssignEvenTickForNonAlias() throws Exception
    {
        Tick t = new Tick(10L);

        t = t.incNonAlias();
        assertEquals(12L, t.getLong());

        t = new Tick(15L);
        t = t.incNonAlias();
        assertEquals(16L, t.getLong());
    }

    @Test
    public void itShouldReportTrueForOddTick() throws Exception
    {
        Tick t = new Tick(101L);
        assertTrue(t.isAlias());
    }

    @Test
    public void itShouldReportFalseForEvenTick() throws Exception
    {
        Tick t = new Tick(100L);
        assertFalse(t.isAlias());
    }
}
