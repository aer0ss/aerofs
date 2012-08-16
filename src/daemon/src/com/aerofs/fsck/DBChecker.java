package com.aerofs.fsck;

import java.sql.SQLException;

import org.apache.log4j.Logger;

import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Util;
import com.google.inject.Inject;

public class DBChecker
{
    private static final Logger l = Util.l(DBChecker.class);
    private final DBCheckOAAndCA _checkOACA;
    private final DBCheckCA _checkCA;
    private final DBCheckFID _checkFID;

    @Inject
    public DBChecker(DBCheckOAAndCA checkAttrs, DBCheckCA checkCA, DBCheckFID checkFID)
            throws SQLException
    {
        _checkOACA = checkAttrs;
        _checkCA = checkCA;
        _checkFID = checkFID;
    }

    public void check_(boolean fix, InOutArg<Boolean> okay)
            throws SQLException
    {
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
        l.warn("ERROR: expected: " + expected);
        l.warn("       offender: " + offender);
    }
}
