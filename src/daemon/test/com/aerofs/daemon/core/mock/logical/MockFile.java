package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.*;

import java.util.SortedMap;
import java.util.TreeMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * See MockRoot for usage.
 */
public class MockFile extends AbstractMockLogicalObject
{
    private final int _branches;

    /**
     * Create a file with MASTER branch only
     */
    public MockFile(String name)
    {
        this(name, 1);
    }

    public MockFile(String name, int branches)
    {
        super(name, Type.FILE, new OID(UniqueID.generate()), false);
        _branches = branches;
    }

    @Override
    protected void mockRecursivelyTypeSpecific(OA oa, MockServices ms) throws ExNotFound
    {
        when(oa.isFile()).thenReturn(true);

        if (!oa.isExpelled()) mockBranches(oa);
    }

    private void mockBranches(OA oa) throws ExNotFound
    {
        assert !oa.isExpelled();

        SortedMap<KIndex, CA> cas = new TreeMap<KIndex, CA>();

        int kMaster = KIndex.MASTER.getInt();
        for (int i = kMaster; i < kMaster + _branches; i++) {
            KIndex kidx = new KIndex(i);

            // mock CA
            CA ca = mock(CA.class);

            // don't use .then(RETURN_MOCKS) here so the client can verify on the mocked object
            when(ca.physicalFile()).thenReturn(mock(IPhysicalFile.class));

            when(oa.ca(kidx)).thenReturn(ca);
            when(oa.caNullable(kidx)).thenReturn(ca);
            when(oa.caThrows(kidx)).thenReturn(ca);

            if (i == kMaster) {
                when(oa.caMaster()).thenReturn(ca);
                when(oa.caMasterNullable()).thenReturn(ca);
                when(oa.caMasterThrows()).thenReturn(ca);
            }

            cas.put(kidx, ca);
        }

        when(oa.cas()).thenReturn(cas);
    }
}
