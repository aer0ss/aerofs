package com.aerofs.sv.server;

import com.aerofs.base.BaseUtil;
import com.aerofs.lib.Util;
import com.aerofs.proto.Sv.PBSVHeader;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.sv.server.SVSchema.C_DEF_AUTO;
import static com.aerofs.sv.server.SVSchema.C_DEF_CFG;
import static com.aerofs.sv.server.SVSchema.C_DEF_DESC;
import static com.aerofs.sv.server.SVSchema.C_DEF_JAVA_ENV;
import static com.aerofs.sv.server.SVSchema.C_HDR_APPROOT;
import static com.aerofs.sv.server.SVSchema.C_HDR_CLIENT;
import static com.aerofs.sv.server.SVSchema.C_HDR_DID;
import static com.aerofs.sv.server.SVSchema.C_HDR_RTROOT;
import static com.aerofs.sv.server.SVSchema.C_HDR_TS;
import static com.aerofs.sv.server.SVSchema.C_HDR_USER;
import static com.aerofs.sv.server.SVSchema.C_HDR_VER;
import static com.aerofs.sv.server.SVSchema.T_DEF;

public class SVDatabase extends AbstractSQLDatabase
{
    public SVDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    static final String C_HDRS = C_HDR_TS + "," + C_HDR_VER + "," +
            C_HDR_USER + "," + C_HDR_DID + "," + C_HDR_APPROOT +
            "," + C_HDR_RTROOT + "," + C_HDR_CLIENT;
    static final String C_HDR_VALUES = "?,?,?,?,?,?,?";

    /**
     * offset because we set 7 of the "common" values in the setHeader() function
     */
    static final int C_HDR_NEXT_COL_INDEX = 8;

    private static void setHeader(PreparedStatement ps, PBSVHeader h, String client)
            throws SQLException
    {
        ps.setLong(1, h.getTime());
        ps.setString(2, h.getVersion());
        ps.setString(3, h.getUser());
        ps.setString(4, BaseUtil.hexEncode(h.getDeviceId().toByteArray()));
        ps.setString(5, h.getAppRoot());
        ps.setString(6, h.getRtRoot());
        ps.setString(7, client);
    }

    // return the id of the error record
    public int insertDefect(PBSVHeader header, String client, boolean automatic, String desc,
            String deviceCfg, String javaEnv)
            throws SQLException
    {
        PreparedStatement psAddDefect = getConnection().prepareStatement(
                "insert into " + T_DEF + "(" + C_HDRS + "," + C_DEF_AUTO +
                        "," + C_DEF_DESC + "," + C_DEF_CFG + "," +
                        C_DEF_JAVA_ENV +
                        ") values (" + C_HDR_VALUES + ",?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS);

        setHeader(psAddDefect, header, client);

        psAddDefect.setBoolean(C_HDR_NEXT_COL_INDEX + 0, automatic);
        psAddDefect.setString(C_HDR_NEXT_COL_INDEX + 1, desc);
        psAddDefect.setString(C_HDR_NEXT_COL_INDEX + 2, deviceCfg);
        psAddDefect.setString(C_HDR_NEXT_COL_INDEX + 3, javaEnv);
        Util.verify(psAddDefect.executeUpdate() == 1);

        ResultSet keys = psAddDefect.getGeneratedKeys();
        try {
            Util.verify(keys.next());
            return keys.getInt(1);
        } finally {
            keys.close();
        }
    }
}
