package com.aerofs.sv.server;

import java.sql.Connection;

import javax.annotation.Nullable;

import com.aerofs.base.BaseUtil;
import com.aerofs.sv.common.Event;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.proto.Sv.PBSVEvent;
import com.aerofs.proto.Sv.PBSVHeader;

import static com.aerofs.sv.server.SVSchema.*;

import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;

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

    /**
     * @param desc may be null
     * @return the unique id of the event
     */
    public int insertEvent(PBSVHeader header, PBSVEvent.Type type, String desc, String client)
            throws SQLException
    {
        PreparedStatement psAddEvent = getConnection().prepareStatement(
                "insert into " + T_EV + "(" + C_HDRS + "," + C_EV_TYPE +
                        "," + C_EV_DESC + ") values (" + C_HDR_VALUES + ",?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS);

        setHeader(psAddEvent, header, client);

        psAddEvent.setInt(C_HDR_NEXT_COL_INDEX + 0, type.getNumber());

        if (desc == null) psAddEvent.setNull(C_HDR_NEXT_COL_INDEX + 1, Types.BLOB);
        else psAddEvent.setString(C_HDR_NEXT_COL_INDEX + 1, desc);

        Util.verify(psAddEvent.executeUpdate() == 1);

        ResultSet keys = psAddEvent.getGeneratedKeys();
        try {
            Util.verify(keys.next());
            return keys.getInt(1);
        } finally {
            keys.close();
        }
    }


    /**
     * add email event to the database
     * @return the unique id associated with this event
     */
    public int insertEmailEvent(EmailEvent ee)
           throws SQLException
    {
        PreparedStatement psAddEmailEvent = getConnection().prepareStatement(
                DBUtil.insert(
                        T_EE, C_EE_EMAIL, C_EE_EVENT, C_EE_DESC, C_EE_CATEGORY, C_EE_TS),
                PreparedStatement.RETURN_GENERATED_KEYS);

        psAddEmailEvent.setString(1, ee._email);
        psAddEmailEvent.setString(2, ee._event.toString().toLowerCase());
        psAddEmailEvent.setString(3, ee._desc);
        psAddEmailEvent.setString(4, ee._category);
        psAddEmailEvent.setLong(5, ee._timestamp);
        Util.verify(psAddEmailEvent.executeUpdate() == 1);

        ResultSet keys = psAddEmailEvent.getGeneratedKeys();
        try {
            Util.verify(keys.next());
            return keys.getInt(1);
        } finally {
            keys.close();
        }
    }

    /**
     * get email event from database based on the unique eventid
     * @return the EmailEvent or null if the event doesn't exist
     * @throws SQLException
     */
    public @Nullable EmailEvent getEmailEvent(int eventId)
        throws SQLException
    {
        PreparedStatement ps = getConnection().prepareStatement(
                        DBUtil.selectWhere(T_EE, C_EE_ID + "=?", C_EE_EMAIL, C_EE_EVENT, C_EE_DESC,
                                C_EE_CATEGORY, C_EE_TS)
                                );

        ps.setInt(1, eventId);

        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) {
                String email = rs.getString(1);
                String event = rs.getString(2);
                String desc = rs.getString(3);
                String cat = rs.getString(4);
                Long timestamp = rs.getLong(5);

                return new EmailEvent(email, Event.valueOf(event.toUpperCase()), desc,
                        cat, timestamp);
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }
}
