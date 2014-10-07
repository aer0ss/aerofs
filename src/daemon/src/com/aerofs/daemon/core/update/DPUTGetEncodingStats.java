/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.Normalizer;
import java.text.Normalizer.Form;

import static com.aerofs.daemon.lib.db.CoreSchema.T_OA;
import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_NAME;
import static com.aerofs.defects.Defects.newMetric;

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
                String name = rs.getString(1);

                if (name == null) {
                    nullNameCount++;
                    continue;
                }

                if (Normalizer.isNormalized(name, Form.NFC)) {
                    // no-op, heuristic to make the searching faster
                } else if (Normalizer.isNormalized(name, Form.NFD)) {
                    nfdCount++;
                } else if (Normalizer.isNormalized(name, Form.NFKC)) {
                    nfkcCount++;
                } else if (Normalizer.isNormalized(name, Form.NFKD)) {
                    nfkdCount++;
                } else {
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
            newMetric("dput.get_encoding_stats")
                    .setMessage(desc)
                    .sendSyncIgnoreErrors();
        }
    }
}
