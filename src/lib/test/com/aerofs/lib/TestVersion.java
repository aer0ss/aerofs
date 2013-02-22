/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.id.DID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestVersion extends AbstractTest
{
    Version v = Version.of(DID.generate(), new Tick(1));

    @Test
    public void whenSubtractingFromSelfShouldBeZero()
    {
        assertTrue(v.sub_(v).isZero_());
    }

    @Test
    public void whenCheckingVersionShadowedByNothingTheResultShouldBeZero()
    {
        assertTrue(v.shadowedBy_(Version.empty()).isZero_());
    }

    @Test
    public void aVersionShouldBeEntirelyShawdowedByItself()
    {
        assertTrue(v.isEntirelyShadowedBy_(v));
    }

    @Test
    public void whenAddingThenSubtractingTheSameVersionNoZeroTicksShouldRemain()
    {
        Version v2 = Version.of(DID.generate(), new Tick(2));
        v2 = v2.add_(v);
        v2 = v2.sub_(v);

        assertFalse(v2.getAll_().values().contains(Tick.ZERO));
    }
}
