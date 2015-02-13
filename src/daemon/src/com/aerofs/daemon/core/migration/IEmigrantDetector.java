/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.migration;

import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SOID;
import com.google.protobuf.ByteString;

import java.util.List;

public interface IEmigrantDetector
{
    /**
     * call this method before applying a metadata update from a remote peer,
     * so that in case of a deletion update, the content of the object or of the
     * object's children, are emigrated instead of deleted because of the update.
     *
     * NB. this method might pause to download anchoring stores or children objects.
     *
     * @param soid the object being downloaded. must be a metadata download
     * @param oidParentTo the parent oid that the object is going to move to
     * @param nameTo the name that the object is going to use
     * @param sidsEmigrantTargetAncestor the value of the emigrant_target_ancestor_sid
     * field from PBMeta
     */
    void detectAndPerformEmigration_(SOID soid, OID oidParentTo, String nameTo,
            List<ByteString> sidsEmigrantTargetAncestor, IDownloadContext cxt)
            throws Exception;

}
