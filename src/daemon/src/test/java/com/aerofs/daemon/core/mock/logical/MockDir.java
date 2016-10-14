package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.OA.Type;

/**
 * See MockRoot for usage.
 */
public class MockDir extends AbstractMockLogicalObject
{
    final AbstractMockLogicalObject[] _children;

    public MockDir(String name, AbstractMockLogicalObject... children)
    {
        super(name, Type.DIR, false);
        _children = children;
    }
}
