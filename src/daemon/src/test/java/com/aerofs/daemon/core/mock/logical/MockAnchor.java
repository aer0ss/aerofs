package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.OA.Type;

/**
 * See MockRoot for usage.
 */
public class MockAnchor extends AbstractMockLogicalObject
{
    final AbstractMockLogicalObject[] _children;

    public MockAnchor(String name, AbstractMockLogicalObject ... children)
    {
        this(name, false, children);
    }

    public MockAnchor(String name, boolean expelled, AbstractMockLogicalObject ... children)
    {
        super(name, Type.ANCHOR, expelled);
        _children = children;
    }
}
