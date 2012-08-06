package com.aerofs.sp.server.sv;

import static com.aerofs.sp.server.sv.SVSchema.C_DEF_AUTO;
import static com.aerofs.sp.server.sv.SVSchema.C_DEF_CFG;
import static com.aerofs.sp.server.sv.SVSchema.C_DEF_DESC;
import static com.aerofs.sp.server.sv.SVSchema.C_DEF_JAVA_ENV;
import static com.aerofs.sp.server.sv.SVSchema.C_EV_DESC;
import static com.aerofs.sp.server.sv.SVSchema.C_EV_TYPE;
import static com.aerofs.sp.server.sv.SVSchema.C_HDR_APPROOT;
import static com.aerofs.sp.server.sv.SVSchema.C_HDR_CLIENT;
import static com.aerofs.sp.server.sv.SVSchema.C_HDR_DID;
import static com.aerofs.sp.server.sv.SVSchema.C_HDR_RTROOT;
import static com.aerofs.sp.server.sv.SVSchema.C_HDR_TS;
import static com.aerofs.sp.server.sv.SVSchema.C_HDR_USER;
import static com.aerofs.sp.server.sv.SVSchema.C_HDR_VER;
import static com.aerofs.sp.server.sv.SVSchema.T_DEF;
import static com.aerofs.sp.server.sv.SVSchema.T_EV;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import com.aerofs.lib.Util;
import com.aerofs.proto.Sv.PBSVEvent;
import com.aerofs.proto.Sv.PBSVHeader;
import com.mysql.jdbc.MysqlDataTruncation;

public class SVDatabase {

    private Connection _c;

    public void init_(String dbEndpoint, String schema, String dbUser, String dbPass)
        throws SQLException, ClassNotFoundException
    {
        Class.forName("com.mysql.jdbc.Driver");

        _c = DriverManager.getConnection(
                "jdbc:mysql://" + dbEndpoint + "/" + schema + "?user=" + dbUser
                + "&password=" + dbPass + "&autoReconnect=true");
    }

    static final String C_HDRS = C_HDR_TS + "," + C_HDR_VER + "," +
        C_HDR_USER + "," + C_HDR_DID + "," + C_HDR_APPROOT +
        "," + C_HDR_RTROOT + "," + C_HDR_CLIENT;
    static final String C_HDR_VALUES = "?,?,?,?,?,?,?";

    /*** offset because we set 7 of the "common" values in the setHeader() function */
    static final int C_HDR_NEXT_COL_INDEX = 8;

    private static void setHeader(PreparedStatement ps, PBSVHeader h,
            String client) throws SQLException
    {
        ps.setLong(1, h.getTime());
        ps.setString(2, h.getVersion());
        ps.setString(3, h.getUser());
        ps.setString(4, Util.hexEncode(h.getDeviceId().toByteArray()));
        ps.setString(5, h.getAppRoot());
        ps.setString(6, h.getRtRoot());
        ps.setString(7, client);
    }

    // return the id of the error record
    private PreparedStatement _psAddDefect;
    public synchronized int addDefect(PBSVHeader header, String client,
            boolean automatic, String desc, String deviceCfg, String javaEnv)
        throws SQLException
    {
        try {
            if (_psAddDefect == null) {
                _psAddDefect = _c.prepareStatement(
                    "insert into " + T_DEF + "(" + C_HDRS + "," + C_DEF_AUTO +
                    "," + C_DEF_DESC + "," + C_DEF_CFG + "," + C_DEF_JAVA_ENV +
                    ") values (" + C_HDR_VALUES + ",?,?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            }

            setHeader(_psAddDefect, header, client);

            _psAddDefect.setBoolean(C_HDR_NEXT_COL_INDEX + 0, automatic);
            _psAddDefect.setString(C_HDR_NEXT_COL_INDEX + 1, desc);
            _psAddDefect.setString(C_HDR_NEXT_COL_INDEX + 2, deviceCfg);
            _psAddDefect.setString(C_HDR_NEXT_COL_INDEX + 3, javaEnv);
            Util.verify(_psAddDefect.executeUpdate() == 1);

            ResultSet keys = _psAddDefect.getGeneratedKeys();
            try {
                Util.verify(keys.next());
                return keys.getInt(1);
            } finally {
                keys.close();
            }

        } catch (SQLException e) {
            _psAddDefect = null;
            throw e;
        }
    }

    /**
     * @param desc may be null
     * @return the unique id of the event
     */
    private PreparedStatement _psAddEvent;
    public synchronized int addEvent(PBSVHeader header, PBSVEvent.Type type, String desc,
            String client) throws SQLException
    {
        try {
            if (_psAddEvent == null) {
                _psAddEvent = _c.prepareStatement(
                    "insert into " + T_EV + "(" + C_HDRS + "," + C_EV_TYPE +
                    "," + C_EV_DESC + ") values (" + C_HDR_VALUES + ",?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            }

            setHeader(_psAddEvent, header, client);

            _psAddEvent.setInt(C_HDR_NEXT_COL_INDEX + 0, type.getNumber());

            if (desc == null) _psAddEvent.setNull(C_HDR_NEXT_COL_INDEX + 1, Types.BLOB);
            else _psAddEvent.setString(C_HDR_NEXT_COL_INDEX + 1, desc);

            Util.verify(_psAddEvent.executeUpdate() == 1);

            ResultSet keys = _psAddEvent.getGeneratedKeys();
            try {
                Util.verify(keys.next());
                return keys.getInt(1);
            } finally {
                keys.close();
            }

        } catch (SQLException e) {
            _psAddEvent = null;

            // for debugging
            if (e instanceof MysqlDataTruncation) {
                Util.l(this).warn("MysqlDataTruncation: hdr_ver=" + header.getVersion());
            }

            throw e;
        }
    }
}
