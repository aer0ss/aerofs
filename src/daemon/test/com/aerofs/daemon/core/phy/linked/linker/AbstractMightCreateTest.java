/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.mock.logical.IsSOIDAtPath;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.phy.linked.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.testlib.AbstractTest;
import org.hamcrest.Description;
import org.junit.Before;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import javax.annotation.Nullable;
import java.sql.SQLException;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class AbstractMightCreateTest extends AbstractTest
{
    @Mock Trans t;
    @Mock DirectoryService ds;
    @Mock InjectableDriver dr;
    @Mock ScanSessionQueue.Factory factSSQ;
    @Mock LinkerRootMap lrm;
    @Mock ILinkerFilter mcf;

    MockDS mds;
    static final String absRootAnchor = "/AeroFS";
    final SID rootSID = SID.generate();

    @Before
    public void setUpAbstract()
    {
        when(lrm.absRootAnchor_(rootSID)).thenReturn(absRootAnchor);

        // 64bit fid: 8 bytes
        when(dr.getFIDLength()).thenReturn(8);
    }

    Path mkpath(String path)
    {
        return Path.fromString(rootSID, path);
    }

    /**
     * Helper to create SOID matcher
     */
    SOID soidAt(String path)
    {
        return argThat(new IsSOIDAtPath(ds, rootSID, path, false));
    }

    /**
     * Helper to create SOID matcher
     */
    OID oidAt(final String path)
    {
        return argThat(new ArgumentMatcher<OID>() {
            private final Path _p = Path.fromString(rootSID, path);
            @Override
            public boolean matches(Object item)
            {
                try {
                    return ((OID)item).equals(ds.resolveNullable_(_p).oid());
                } catch (SQLException e) {
                    throw new AssertionError();
                }
            }
            @Override
            public void describeTo(Description description)
            {
                description.appendText("<OID @ ").appendValue(_p).appendText(">");
            }
        });
    }

    FIDAndType generateFileFnt() throws SQLException
    {
        return generateFileFnt(null);
    }

    FIDAndType generateDirFnt() throws SQLException
    {
        return generateDirFnt(null);
    }

    FIDAndType generateDirFnt(@Nullable SOID soid) throws SQLException
    {
        return new FIDAndType(generateFID(soid), true);
    }

    FIDAndType generateFileFnt(@Nullable SOID soid) throws SQLException
    {
        return new FIDAndType(generateFID(soid), false);
    }

    FID generateFID(@Nullable SOID soid) throws SQLException
    {
        byte[] bs = new byte[dr.getFIDLength()];
        Util.rand().nextBytes(bs);
        FID fid = new FID(bs);
        if (soid != null) {
            OA oa = ds.getOANullable_(soid);
            if (oa != null) when(oa.fid()).thenReturn(fid);
            when(ds.getSOIDNullable_(eq(fid))).thenReturn(soid);
        }
        return fid;
    }
}
