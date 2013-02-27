package com.aerofs.fsck;

import java.sql.SQLException;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import com.aerofs.lib.InOutArg;
import com.google.inject.Inject;

public class DBChecker
{
    private static final Logger l = Loggers.getLogger(DBChecker.class);
    private final DBCheckOAAndCA _checkOACA;
    private final DBCheckCA _checkCA;
    private final DBCheckFID _checkFID;
    private final DBCheckIntegrity _checkIntegrity;

    @Inject
    public DBChecker(DBCheckOAAndCA checkAttrs, DBCheckCA checkCA, DBCheckFID checkFID,
            DBCheckIntegrity checkIntegrity)
            throws SQLException
    {
        _checkOACA = checkAttrs;
        _checkCA = checkCA;
        _checkFID = checkFID;
        _checkIntegrity = checkIntegrity;
    }

    public void check_(boolean fix, InOutArg<Boolean> okay)
            throws SQLException
    {
        l.warn("checking database integrity...");
        _checkIntegrity.check_(okay);

        l.warn("checking meta/content consistency...");
        _checkOACA.check_(okay);

        l.warn("checking content consistency...");
        _checkCA.check_(okay);

        l.warn("checking fid consistency...");
        _checkFID.check_(fix, okay);
    }

    static void error(String expected, String offender, InOutArg<Boolean> okay)
    {
        okay.set(false);

        String str1 = "ERROR: expected: " + expected;
        String str2 = "       offender: " + offender;
        l.warn(str1);
        l.warn(str2);
        System.out.println(str1);
        System.out.println(str2);
    }
}
