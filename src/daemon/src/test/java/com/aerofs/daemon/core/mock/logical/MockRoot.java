package com.aerofs.daemon.core.mock.logical;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSAnchor;
import com.aerofs.daemon.core.mock.logical.MockDS.MockDSDir;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import javax.annotation.Nullable;

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

    private MockDS mds;

    public MockRoot(AbstractMockLogicalObject ... children)
    {
        _children = children;
    }

    public void mock(SID rootSID, DirectoryService ds, @Nullable IMapSID2SIndex sid2sidx,
            @Nullable IMapSIndex2SID sidx2sid) throws Exception
    {
        mds = new MockDS(rootSID, ds, sid2sidx, sidx2sid);
        for (AbstractMockLogicalObject o : _children) {
            mock(mds.root(), o);
        }
    }

    private void mock(MockDSDir d, AbstractMockLogicalObject o) throws Exception
    {
        switch (o._type) {
        case FILE:
            MockFile mfile = (MockFile)o;
            o._oid = d.file(o._name, mfile._branches).soid().oid();
            break;
        case DIR:
            MockDir mdir = (MockDir)o;
            MockDSDir dir = d.dir(o._name);
            o._oid = dir.soid().oid();
            for (AbstractMockLogicalObject c : mdir._children) {
                mock(dir, c);
            }
            break;
        case ANCHOR:
            MockAnchor manchor = (MockAnchor)o;
            MockDSAnchor anchor = d.anchor(o._name, o._expelled);
            o._oid = anchor.soid().oid();
            for (AbstractMockLogicalObject c : manchor._children) {
                mock(anchor._root, c);
            }
            break;
        }
    }
}
