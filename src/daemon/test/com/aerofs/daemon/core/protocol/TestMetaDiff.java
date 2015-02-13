package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.lib.id.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.aerofs.proto.Core.PBMeta;
import com.aerofs.testlib.AbstractTest;

import java.sql.SQLException;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * Simple unit test for MetaDiff class.
 */
public class TestMetaDiff extends AbstractTest
{
    private static final String name = "Dummy";
    private static final OID parent = new OID(UniqueID.generate());

    @Mock DirectoryService mockedDS;
    @Mock LocalACL _mockedLACL;
    @InjectMocks MetaDiff mdiff;

    private PBMeta mockedMeta;

    @Before
    public void setup()
    {
        // Protobuf classes can't be mocked by Mockito since they are static final
        // Java classes.

        mockedMeta = PBMeta.newBuilder()
            .setType(PBMeta.Type.FILE)
            .setParentObjectId(BaseUtil.toPB(parent))
            .setName(name)
            .setFlags(0x0)
            .build();
    }

    @Test
    public void shouldSetParentNameOwnerFlagsForNewObject() throws SQLException
    {
        SOID soidNew = new SOID(new SIndex(1), new OID(UniqueID.generate()));

        // Since object is new, locally object has no attribute or ACE information.
        when(mockedDS.getOANullable_(soidNew)).thenReturn(null);

        int diff = mdiff.computeMetaDiff_(soidNew, mockedMeta,  parent);

        // Verify PARENT, NAME and OWNER flags are set for new object.
        int expectedFlagsSet =  MetaDiff.PARENT | MetaDiff.NAME;
        assertEquals(expectedFlagsSet, diff & expectedFlagsSet);
    }
}
