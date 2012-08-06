package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.AbstractMockLogicalObject.MockServices;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import javax.annotation.Nullable;

import java.sql.SQLException;

/**
 * This class and related classes mock logical objects and wires related services accordingly.
 * Usage:
 *
 *          MockRoot root =
 *                  new MockRoot(
 *                      new MockFile("f1", 2),
 *                      new MockDir("d2",
 *                          new MockFile("f2.2"),
 *                          new MockAnchor("a2.3",
 *                              new MockDir("d2.3.1"),
 *                              new MockAnchor("a2.3.3")
 *                          )
 *                      )
 *                  );
 *
 *          DirectoryService ds = mock(DirectoryService.class);
 *          root.mock(ds, null, null);
 *          LogicalObjectsPrinter.printRecursively(ds2);
 *
 * This will print:
 *
 *  0 [main] INFO @lib.Util  - 592<0000> /
 *  2 [main] INFO @lib.Util  - 592<ebfc> /f1
 *  3 [main] INFO @lib.Util  - 592<e409> /d2/
 *  4 [main] INFO @lib.Util  - 592<f1ec> /d2/f2.2
 *  5 [main] INFO @lib.Util  - 592<33ac> /d2/a2.3*
 *  6 [main] INFO @lib.Util  - 688<edc0> /d2/a2.3/d2.3.1/
 *  7 [main] INFO @lib.Util  - 688<c55a> /d2/a2.3/a2.3.3*
 *
 * Note that directories with be printed with a trailing slash, and anchors with a trailing star.
 *
 */
public class MockRoot
{
    private final AbstractMockLogicalObject[] _children;
    private final SID _sid;

    public MockRoot(AbstractMockLogicalObject ... children)
    {
        _children = children;
        _sid = new SID(UniqueID.generate());
    }

    public void mock(DirectoryService ds, @Nullable IMapSID2SIndex sid2sidx,
             @Nullable IMapSIndex2Store sidx2s)
             throws ExNotFound, SQLException, ExNotDir, ExExpelled
    {
        MockServices ms = new MockServices(ds, sid2sidx, sidx2s);

        Store s = MockAnchor.mockStore(_sid, new Path(), _children, ms);

        SIndex sidx = s.sidx();

        // AbstractMockLogicalObject doesn't mock path resolution for roots, so we do it manually.
        AbstractMockLogicalObject.mockPathResolution(ds, new Path(), new SOID(sidx, OID.ROOT));
    }
}
