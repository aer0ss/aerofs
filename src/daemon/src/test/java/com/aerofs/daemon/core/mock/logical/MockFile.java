package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.OA.Type;

/**
 * See MockRoot for usage.
 */
public class MockFile extends AbstractMockLogicalObject
{
    final int _branches;

    /**
     * Create a file with MASTER branch only
     */
    public MockFile(String name)
    {
        this(name, 1);
    }

    public MockFile(String name, int branches)
    {
        super(name, Type.FILE, false);
        _branches = branches;
    }
}
