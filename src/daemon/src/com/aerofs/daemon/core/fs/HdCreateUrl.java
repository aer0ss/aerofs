/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.fs.EICreateUrl;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Sp.CreateUrlReply;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.inject.Inject;

/**
 * Handler class for link sharing through the shell ext. Gets the path of file/folder whose link
 * is to be shared, resolves its SOID and makes relevant SP calls to do the same. Returns the link
 * to the GUI.
 */
public class HdCreateUrl extends AbstractHdIMC<EICreateUrl>
{
    private final TokenManager _tokenManager;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final SPBlockingClient.Factory _factSP;

    @Inject
    public HdCreateUrl(TokenManager tokenManager, DirectoryService ds,
            InjectableSPBlockingClientFactory factSP, IMapSIndex2SID sidx2sid)
    {
        _tokenManager = tokenManager;
        _ds = ds;
        _factSP = factSP;
        _sidx2sid = sidx2sid;
    }

    @Override
    protected void handleThrows_(EICreateUrl ev, Prio prio)
            throws Exception
    {
        l.info("Creating link for : {}", ev._path);

        SOID soid = _ds.resolveThrows_(ev._path);

        // can't share root folder or trash
        if (soid.oid().isRoot() || soid.oid().isTrash()) {
            throw new ExNoPerm("can't share system folders");
        }
        ev.setResult(getLinkFromSP(soid));
    }

    private String getLinkFromSP(SOID soid) throws Exception
    {
        // For link sharing, the soid provided is a concatenation of SID + OID. Refer to
        // RestObject for more details.
        String linkSharingSoid =
                _sidx2sid.get_(soid.sidx()).toStringFormal() + soid.oid().toStringFormal();
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "sp-create-url");
        try {
            TCB tcb = tk.pseudoPause_("sp-create-url");
            try {
                CreateUrlReply urlReply = _factSP.create()
                        .signInRemote()
                        .createUrl(linkSharingSoid);
                return urlReply.getUrlInfo().getKey();
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}