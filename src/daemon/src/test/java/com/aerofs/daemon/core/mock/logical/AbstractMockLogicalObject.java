package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.ids.OID;

/**
 * See MockRoot for usage.
 */
public abstract class AbstractMockLogicalObject
{
    OID _oid;
    final String _name;
    final Type _type;
    final boolean _expelled;

    AbstractMockLogicalObject(String name, Type type, boolean expelled)
    {
        _name = name;
        _type = type;
        _expelled = expelled;
    }
}
