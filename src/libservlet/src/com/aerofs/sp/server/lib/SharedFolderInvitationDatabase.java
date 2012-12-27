/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_FI_FIC;
import static com.aerofs.sp.server.lib.SPSchema.C_FI_FOLDER_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_FI_FROM;
import static com.aerofs.sp.server.lib.SPSchema.C_FI_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_FI_SID;
import static com.aerofs.sp.server.lib.SPSchema.C_FI_TO;
import static com.aerofs.sp.server.lib.SPSchema.T_FI;

public class SharedFolderInvitationDatabase extends AbstractSQLDatabase
{
    public SharedFolderInvitationDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void insert(UserID sharer, UserID sharee, SID sid, String folderName, Role role,
            String code)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_FI, C_FI_FIC, C_FI_FROM, C_FI_TO, C_FI_SID, C_FI_FOLDER_NAME,
                        C_FI_ROLE));

        ps.setString(1, code);
        ps.setString(2, sharer.toString());
        ps.setString(3, sharee.toString());
        ps.setBytes(4, sid.getBytes());
        ps.setString(5, folderName);
        ps.setInt(6, role.ordinal());

        ps.executeUpdate();
    }

    /**
     * Return all the invitation codes belonging to the given sharee. At most one code is returned
     * for each shared folder.
     */
    public Collection<String> getAll(UserID sharee) throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_FI, C_FI_TO + " = ? group by " + C_FI_SID, C_FI_FIC));

        ps.setString(1, sharee.toString());
        ResultSet rs = ps.executeQuery();
        try {
            List<String> codes = Lists.newArrayList();
            while (rs.next()) codes.add(rs.getString(1));
            return codes;
        } finally {
            rs.close();
        }
    }

    public @Nonnull UserID getSharee(String code)
            throws SQLException, ExNotFound
    {
        return UserID.fromInternal(querySFIString(code, C_FI_TO));
    }

    public @Nonnull UserID getSharer(String code)
            throws SQLException, ExNotFound
    {
        return UserID.fromInternal(querySFIString(code, C_FI_FROM));
    }

    /**
     * TODO (WW) clients should use use SharedFolderDatabase.getName() instead.
     */
    public @Nonnull String getFolderName(String code)
            throws SQLException, ExNotFound
    {
        return querySFIString(code, C_FI_FOLDER_NAME);
    }

    public @Nonnull SID getSID(String code)
            throws SQLException, ExNotFound
    {
        ResultSet rs = querySFI(code, C_FI_SID);
        try {
            return new SID(rs.getBytes(1));
        } finally {
            rs.close();
        }
    }

    public @Nonnull Role getRole(String code)
            throws SQLException, ExNotFound
    {
        ResultSet rs = querySFI(code, C_FI_ROLE);
        try {
            return Role.fromOrdinal(rs.getInt(1));
        } finally {
            rs.close();
        }
    }

    private @Nonnull String querySFIString(String code, String field)
            throws SQLException, ExNotFound
    {
        ResultSet rs = querySFI(code, field);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    private ResultSet querySFI(String code, String field)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_FI, C_FI_FIC + "=?", field));
        ps.setString(1, code);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("shared folder invitation " + code);
        } else {
            return rs;
        }
    }
}
