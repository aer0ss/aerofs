/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.spsv.SVClient;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.Normalizer.Form;

import static com.aerofs.lib.db.CoreSchema.T_OA;
import static com.aerofs.lib.db.CoreSchema.C_OA_NAME;

public class DPUTGetEncodingStats implements IDaemonPostUpdateTask
{
    private final IDBCW _dbcw;

    DPUTGetEncodingStats(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    @Override
    public void run() throws Exception
    {
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        long nfdCount = 0, nfkcCount = 0, nfkdCount = 0, unknownCount = 0, nullNameCount = 0;

        try {
            ResultSet rs = s.executeQuery("select " + C_OA_NAME + " from " + T_OA);
            while (rs.next()) {
                String name = rs.getString(0);

                if (name == null) {
                    nullNameCount++;
                    continue;
                }

                try {
                    Form form = Form.valueOf(name);
                    switch (form) {
                    case NFD:       nfdCount++;     break;
                    case NFKC:      nfkcCount++;    break;
                    case NFKD:      nfkdCount++;    break;
                    default:        break;
                    }
                } catch (IllegalArgumentException e) {
                    unknownCount++;
                }
            }
        } finally {
            s.close();
        }
        c.commit();

        long totalCount = nfdCount + nfkcCount + nfkdCount + unknownCount + nullNameCount;
        if (totalCount > 0) {
            String desc = "Non NFC-form file names detected:\n" +
                    "NFD       count: " + nfdCount + "\n" +
                    "NFKC      count: " + nfkcCount + "\n" +
                    "NFKD      count: " + nfkdCount + "\n" +
                    "null name count: " + nullNameCount + "\n" +
                    "unknown   count: " + unknownCount + "\n" +
                    "total     count: " + totalCount;
            SVClient.logSendDefectNoLogsIgnoreErrors(true, desc, null);
        }
    }
}
