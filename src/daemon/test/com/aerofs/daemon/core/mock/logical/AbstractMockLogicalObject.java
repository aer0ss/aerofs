package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import org.mockito.ArgumentMatcher;

import javax.annotation.Nullable;

import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.argThat;

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
