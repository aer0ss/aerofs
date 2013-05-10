/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser.migration;

import com.aerofs.daemon.core.download.IDownloadContext;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOID;
import com.google.protobuf.ByteString;

import java.util.List;

public class NullEmigrantDetector implements IEmigrantDetector
{
    @Override
    public void detectAndPerformEmigration_(SOID soid, OID oidParentTo, String nameTo,
            List<ByteString> sidsEmigrantTargetAncestor, IDownloadContext cxt)
            throws Exception
    {
    }
}
