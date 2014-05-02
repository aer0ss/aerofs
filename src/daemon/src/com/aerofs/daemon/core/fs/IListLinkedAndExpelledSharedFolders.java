/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Ritual.PBSharedFolder;

import javax.annotation.Nullable;
import java.sql.SQLException;

/**
 * List Linked and Expelled shared folders interface. This interface is implemented by single user
 * and multiuser(TS) client.
 */
public interface IListLinkedAndExpelledSharedFolders
{
    /**
     * This will return all shared folders that are either linked/admitted or expelled. Note: We see
     * expelled folders only in the case of single user clients, not multiuser.
     */
    @Nullable PBSharedFolder getSharedFolder(SIndex sIndex, SID sid) throws SQLException;
}