/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.acl;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * Database backend for {@link SharedFolderAutoLeaver}
 */
public class SharedFolderLeaveQueueDatabase extends AbstractDatabase
{
    @Inject
    public SharedFolderLeaveQueueDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    class SIDIterator extends AbstractDBIterator<SID>
    {
        public SIDIterator(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public SID get_() throws SQLException
        {
            return new SID(_rs.getBytes(1));
        }
    }

    private PreparedStatement _psGetLeaveCommands;
    public IDBIterator<SID> getLeaveCommands_() throws SQLException
    {
        try {
            if (_psGetLeaveCommands == null) {
                _psGetLeaveCommands = c().prepareStatement(DBUtil.select(T_SPQ, C_SPQ_SID) +
                        " order by " + C_SPQ_IDX);
            }
            ResultSet rs = _psGetLeaveCommands.executeQuery();
            return new SIDIterator(rs);
        } catch (SQLException e) {
            DBUtil.close(_psGetLeaveCommands);
            _psGetLeaveCommands = null;
            throw e;
        }
    }

    private PreparedStatement _psAddLeaveCommand;
    public void addLeavecommand_(SID sid, Trans t) throws SQLException
    {
        try {
            if (_psAddLeaveCommand == null) {
                _psAddLeaveCommand = c().prepareStatement(DBUtil.insert(T_SPQ, C_SPQ_SID));
            }
            _psAddLeaveCommand.setBytes(1, sid.getBytes());
            int rows = _psAddLeaveCommand.executeUpdate();
            assert rows == 1;
        } catch (SQLException e) {
            DBUtil.close(_psAddLeaveCommand);
            _psAddLeaveCommand = null;
            throw e;
        }
    }

    private PreparedStatement _psRemoveLeaveCommand;
    public void removeLeaveCommands_(SID sid, Trans t) throws SQLException
    {
        try {
            if (_psRemoveLeaveCommand == null) {
                _psRemoveLeaveCommand = c().prepareStatement(
                        DBUtil.deleteWhere(T_SPQ, C_SPQ_SID + "=?"));
            }
            _psRemoveLeaveCommand.setBytes(1, sid.getBytes());
            _psRemoveLeaveCommand.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psRemoveLeaveCommand);
            _psRemoveLeaveCommand = null;
            throw e;
        }
    }
}
