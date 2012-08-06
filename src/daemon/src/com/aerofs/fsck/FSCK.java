package com.aerofs.fsck;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreDatabaseDumper;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.spsv.SVClient;
import com.google.inject.Inject;

import java.sql.SQLException;

public class FSCK
{
    private final IDBCW _dbcw;
    private final DBChecker _checker;
    private final CoreDatabaseDumper _dump;

    @Inject
    public FSCK(CoreDatabaseDumper dump, DBChecker checker, CoreDBCW dbcw)
    {
        _dump = dump;
        _checker = checker;
        _dbcw = dbcw.get();
    }

    void init_() throws SQLException
    {
        _dbcw.init_();
    }

    void run_(String[] args) throws Exception
    {
        // TODO:
        // * "PRAGMA integrity_check" for sqlite
        // * "VACUUM" sqlite
        // * check db consistency
        // * check consistency between file presence and physical files
        // * for each present branch <=> !local_version.isZero()
        // * check according to all the assertions in OA and CA
        //

        boolean check = true;
        boolean formal = false;
        boolean repair = false;
        int i;
        String tableName = null;
        for (i = 0; i < args.length; i++) {
            if (args[i].equals("-l")) check = false;
            else if (args[i].equals("-f")) formal = true;
            else if (args[i].equals("-r")) repair = true;
            else if (args[i].equals("-t"))  {
                tableName = args[++i];
            } else throw new ExBadArgs(args[i]);
        }


        if (check) {
            InOutArg<Boolean> okay = new InOutArg<Boolean>(true);

            _checker.check_(repair, okay);

            if (!okay.get()) {
                SVClient.logSendDefectSync(true, "fsck found inconsistency. see logs for detail",
                        new Exception());
            }

        } else if ("attr".equals(tableName)) {
            _dump.dumpAttr_(System.out, formal);
        } else if ("ver".equals(tableName)) {
            _dump.dumpVer_(System.out, formal);
        } else if ("alias".equals(tableName)) {
            _dump.dumpAlias_(System.out, formal);
        } else {
            _dump.dumpAll_(System.out, formal);
        }

    }
}
