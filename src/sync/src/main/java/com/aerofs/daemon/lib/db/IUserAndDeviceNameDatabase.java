/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.FullName;
import com.aerofs.ids.UserID;

import javax.annotation.Nullable;
import java.sql.SQLException;

/**
 * When possible, use the UserAndDeviceNames class which provides a high-level wrapper around this
 * low-level class.
 */
public interface IUserAndDeviceNameDatabase
{
    void clearUserNameCache_() throws SQLException;
    void clearDeviceNameCache_() throws SQLException;

    /**
     * @param name null if it is not revealed by SP. See getDeviceNameNullable_ for detail
     */
    void setDeviceName_(DID did, @Nullable String name, Trans t)
            throws SQLException;

    void setUserName_(UserID user, FullName fullName, Trans t)
            throws SQLException;

    /**
     * @return null if the entry doesn't exist locally, i.e., if the device is "unresolved"
     */
    @Nullable String getDeviceNameNullable_(DID did)
            throws SQLException;

    /**
     * @return null if the entry doesn't exist
     */
    @Nullable FullName getUserNameNullable_(UserID user)
            throws SQLException;
}
