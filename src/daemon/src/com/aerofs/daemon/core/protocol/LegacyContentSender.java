package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

public class LegacyContentSender extends ContentSender
{
    private static final Logger l = Loggers.getLogger(LegacyContentSender.class);

    @Inject
    public LegacyContentSender(UploadState ulstate, CoreScheduler sched,
                               TransportRoutingLayer trl, Metrics m, TokenManager tokenManager,
                               CfgStorageType cfgStorageType)
    {
        super(ulstate, sched, trl, m, tokenManager, cfgStorageType);
        // TODO: abort ongoing download on hash change
    }

    ContentHash send_(
            Endpoint ep,
            SendableContent content,
            PBCore.Builder bdCore,
            PBGetComponentResponse.Builder bdResponse,
            long prefixLen,
            @Nullable ContentHash remoteHash)
            throws Exception
    {
        try {
            return sendInternal_(ep, content, bdCore, bdResponse, prefixLen, remoteHash);
        } catch (ExUpdateInProgress e) {
            content.pf.onUnexpectedModification_(content.mtime);
            throw e;
        }
    }

    private ContentHash sendInternal_(
            Endpoint ep,
            SendableContent content,
            PBCore.Builder bdCore,
            PBGetComponentResponse.Builder bdResponse,
            long prefixLen,
            @Nullable ContentHash remoteHash)
            throws Exception
    {
        bdResponse.setFileTotalLength(content.length);
        bdResponse.setMtime(content.mtime);

        // Send hash if available.
        final ContentHash h = content.hash;
        boolean contentIsSame = false;

        if (h != null) {
            if (remoteHash != null && h.equals(remoteHash)) {
                contentIsSame = true;
                bdResponse.setIsContentSame(true);
                l.info("Content same");
            } else {
                l.info("Sending hash: {}", h);
                bdResponse.setHash(h.toPB());
            }
        } else {
            // refuse to serve content until the hash is known
            // NB: for backwards compat reason, this cannot be used for BlockStorage clients
            // deployed before incremental prefix hashing was introduced, as files larger than
            // the block size transferred before that may not have a valid content hash.
            // For linked storage the linker/scanner will eventually compute the hash (assuming
            // it is not modified faster than the hash can be computed but in that case we have
            // no hope of ever transferring it anyway...)
            if (Cfg.storageType() == StorageType.LINKED) {
                throw new ExUpdateInProgress("wait for hash to serve content");
            }
        }

        if (prefixLen > 0) {
            bdResponse.setPrefixLength(prefixLen);
        }

        PBCore response = bdCore.setGetComponentResponse(bdResponse).build();
        ByteArrayOutputStream os = Util.writeDelimited(response);
        if (!bdResponse.hasPrefixLength() && os.size() <= _m.getMaxUnicastSize_() && contentIsSame) {
            sendContentSame_(ep, os, response);
        } else if (!bdResponse.hasPrefixLength() && os.size() + content.length <= _m.getMaxUnicastSize_()) {
            return sendSmall_(ep, content, os, response);
        } else {
            MessageDigest md = createDigestIfNeeded_(prefixLen, content.pf);
            try (Token tk = _tokenManager.acquireThrows_(Cat.SERVER,
                    "SendContent(" + content.sokid + ", " + ep + ")")) {
                return sendBig_(ep, content, os, prefixLen, tk, md);
            }
        }
        return null;
    }

    private void sendContentSame_(Endpoint ep, ByteArrayOutputStream os, PBCore response)
            throws Exception
    {
        _trl.sendUnicast_(ep, CoreProtocolUtil.typeString(response), response.getRpcid(), os);
    }

    private @Nullable MessageDigest createDigestIfNeeded_(long prefixLen, IPhysicalFile pf)
            throws Exception
    {
        // see definition of PREFIX_REHASH_MAX_LENGTH for rationale of rehash limit on prefix length
        if (prefixLen >= DaemonParam.PREFIX_REHASH_MAX_LENGTH) return null;

        MessageDigest md = SecUtil.newMessageDigest();
        if (prefixLen == 0) return md;

        try (InputStream is = pf.newInputStream()) {
            ByteStreams.copy(ByteStreams.limit(is, prefixLen),
                    new DigestOutputStream(ByteStreams.nullOutputStream(), md));
        }
        return md;
    }
}
