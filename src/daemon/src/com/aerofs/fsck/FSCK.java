package com.aerofs.fsck;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.CoreDatabaseDumper;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.base.ex.ExBadArgs;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.defects.Defects.newDefectWithLogs;

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
                newDefectWithLogs("fsck.consistency")
                        .setMessage("fsck found inconsistency. see logs for detail")
                        .sendSync();
            }

        } else {
            Statement s = _dbcw.getConnection().createStatement();
            try {
                if ("attr".equals(tableName)) {
                    _dump.dumpAttr_(s, System.out, formal);
                } else if ("ver".equals(tableName)) {
                    _dump.dumpVer_(s, System.out, formal);
                } else if ("alias".equals(tableName)) {
                    _dump.dumpAlias_(s, System.out, formal);
                } else {
                    _dump.dumpAll_(s, System.out, formal);
                }
            } catch (SQLException e) {
                System.err.println("An error occured while dumping the db: " + e);
                s.close();
            }
        }
    }
}
