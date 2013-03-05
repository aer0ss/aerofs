/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.acl;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.sql.SQLException;

public class ACLChecker
{
    private final LocalACL _lacl;
    private final DirectoryService _ds;

    @Inject
    ACLChecker(LocalACL lacl, DirectoryService ds)
    {
        _lacl = lacl;
        _ds = ds;
    }


    /**
     * @return the SOID corresponding to the specified path
     */
    public @Nonnull SOID checkThrows_(UserID subject, Path path, Role role)
            throws ExNotFound, SQLException, ExNoPerm, ExExpelled
    {
        SOID soid = _ds.resolveThrows_(path);
        OA oa = _ds.getOAThrows_(soid);
        if (oa.isAnchor()) soid = _ds.followAnchorThrows_(oa);
        checkThrows_(subject, soid.sidx(), role);
        return soid;
    }

    /**
     * @return the SOID corresponding to the specified path. Do not follow anchor if the resolved
     * object is an anchor.
     */
    public @Nonnull SOID checkNoFollowAnchorThrows_(UserID subject, Path path, Role role)
            throws ExNotFound, SQLException, ExNoPerm
    {
        SOID soid = _ds.resolveThrows_(path);
        checkThrows_(subject, soid.sidx(), role);
        return soid;
    }


    public void checkThrows_(UserID subject, SIndex sidx, Role role)
            throws SQLException, ExNoPerm, ExNotFound
    {
        if (!_lacl.check_(subject, sidx, role)) throw new ExNoPerm(subject + ", " + role + ", " + sidx);
    }

}
