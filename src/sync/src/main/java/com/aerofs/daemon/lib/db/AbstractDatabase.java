package com.aerofs.daemon.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.lib.db.PreparedStatementWrapper;

import com.aerofs.lib.db.dbcw.IDBCW;

public abstract class AbstractDatabase
{
    protected final IDBCW _dbcw;

    protected AbstractDatabase(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    protected Connection c() throws SQLException
    {
        return _dbcw.getConnection();
    }

    public ResultSet query(PreparedStatementWrapper psw,
                           Object p0) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setObject(1, p0);
            return ps.executeQuery();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    public ResultSet query(PreparedStatementWrapper psw,
                           Object p0, Object p1) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setObject(1, p0);
            ps.setObject(2, p1);
            return ps.executeQuery();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    public ResultSet query(PreparedStatementWrapper psw,
                           Object p0, Object p1, Object p2) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setObject(1, p0);
            ps.setObject(2, p1);
            ps.setObject(3, p2);
            return ps.executeQuery();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    public ResultSet query(PreparedStatementWrapper psw,
                           Object ...p) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            for (int i = 0; i < p.length; ++i) {
                ps.setObject(i, p[i]);
            }
            return ps.executeQuery();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    public int update(PreparedStatementWrapper psw,
                      Object p0) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setObject(1, p0);
            return ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    public int update(PreparedStatementWrapper psw,
                      Object p0, Object p1) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setObject(1, p0);
            ps.setObject(2, p1);
            return ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    public int update(PreparedStatementWrapper psw,
                      Object p0, Object p1, Object p2) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setObject(1, p0);
            ps.setObject(2, p1);
            ps.setObject(3, p2);
            return ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    public int update(PreparedStatementWrapper psw,
                      Object... p) throws SQLException {
        try {
            PreparedStatement ps = psw.get(c());
            for (int i = 0; i < p.length; ++i) {
                ps.setObject(1 + i, p[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    protected SQLException detectCorruption(SQLException e)
    {
        _dbcw.throwIfDBCorrupted(e);
        return e;
    }
}
