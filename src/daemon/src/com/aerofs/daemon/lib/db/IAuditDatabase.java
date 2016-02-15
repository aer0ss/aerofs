/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ClientParam;

import java.sql.SQLException;

/**
 * Implemented by classes that store/retrieve
 * persistent data for the auditing subsystem.
 */
public interface IAuditDatabase
{
    /**
     * Get the index of the last activity row reported to the auditor.
     *
     * @return {@link ClientParam#INITIAL_AUDIT_PUSH_EPOCH}
     * if no activity row has been reported to the auditor, or a positive
     * index otherwise
     *
     * @throws SQLException if the select operation on the audit database fails
     */
    long getLastReportedActivityRow_() throws SQLException;

    /**
     * Set the index of the last activity row reported to the auditor.
     *
     * @param activityRowIndex index (>=0) of the activity row to store
     * @param t instance of {@code Trans} within which this update is performed.
     *
     * @throws SQLException if the update operation on the audit database fails
     */
    void setLastReportedActivityRow_(long activityRowIndex, Trans t) throws SQLException;
}
