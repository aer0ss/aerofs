/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.umdc;

import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.sv.client.SVClient;
import com.google.common.io.Files;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.T_AL;

public class UMDC
{
    private static final Logger l = Util.l(UMDC.class);
    private IDBCW _db;

    public UMDC()
    {
        ObfuscatedDatabaseParams params = new ObfuscatedDatabaseParams(Param.OBF_CORE_DATABASE);
        _db = DBUtil.newDBCW(params);
    }

    private enum Commands {
        UPLOAD_DATABASE
    }

    public void run_(@Nonnull String[] args) throws SQLException, IOException
    {
        try {
            Commands cmd = Commands.valueOf(args[0]);
            switch (cmd) {
            case UPLOAD_DATABASE:
                uploadDatabase();
                break;
            default:
                l.warn("Unknown cmd: " + args[0]);
                break;
            }
        } catch (IllegalArgumentException e) {
            l.warn("Illegal cmd: " + args[0]);
        }
    }

    private void uploadDatabase() throws SQLException, IOException
    {
        copyDBFiles();

        ObfuscatedDatabase obfdb = new ObfuscatedDatabase(_db);
        obfdb.init_();
        obfdb.obfuscate();
        obfdb.pruneTables(T_AL);
        obfdb.vacuum();
        obfdb.finish_();

        SVClient.sendCoreDatabaseSync();

        removeObfuscatedDB();
    }

    private void copyDBFiles() throws IOException
    {
        String from = Util.join(Cfg.absRTRoot(), Param.CORE_DATABASE);
        String to = Util.join(Cfg.absRTRoot(), Param.OBF_CORE_DATABASE);
        Files.copy(new File(from), new File(to));
    }

    private void removeObfuscatedDB() throws IOException
    {
        String path = Util.join(Cfg.absRTRoot(), Param.OBF_CORE_DATABASE);
        File obfuscatedDB = new File(path);
        obfuscatedDB.delete();
    }
}
