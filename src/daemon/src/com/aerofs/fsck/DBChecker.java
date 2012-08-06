package com.aerofs.fsck;

import java.sql.SQLException;

import org.apache.log4j.Logger;

import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Util;
import com.google.inject.Inject;

public class DBChecker
{
    private static final Logger l = Util.l(DBChecker.class);
    private final DBCheckAttrs _checkAttrs;
    private final DBCheckFID _checkFID;

    @Inject
    public DBChecker(DBCheckAttrs checkAttrs, DBCheckFID checkFID) throws SQLException
    {
        _checkAttrs = checkAttrs;
        _checkFID = checkFID;
    }

    public void check_(boolean fix, InOutArg<Boolean> okay)
            throws SQLException
    {
        l.warn("checking m/c consistency...");
        _checkAttrs.check_(okay);

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
