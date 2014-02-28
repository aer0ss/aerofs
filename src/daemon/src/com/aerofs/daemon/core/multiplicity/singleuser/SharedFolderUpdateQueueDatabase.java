/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.multiplicity.singleuser.ISharedFolderOp.SharedFolderOpType;
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
import java.sql.Types;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * Database backend for {@link SharedFolderAutoUpdater}
 */
class SharedFolderUpdateQueueDatabase extends AbstractDatabase
{
    @Inject
    public SharedFolderUpdateQueueDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    class SharedFolderOpIterator extends AbstractDBIterator<ISharedFolderOp>
    {
        public SharedFolderOpIterator(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ISharedFolderOp get_() throws SQLException
        {
            SID sid = new SID(_rs.getBytes(1));
            SharedFolderOpType type = SharedFolderOpType.fromValue(_rs.getInt(2));
            ISharedFolderOp op;
            if (type == SharedFolderOpType.LEAVE){
                op = new LeaveOp(sid);
            } else if (type == SharedFolderOpType.RENAME){
                String name = _rs.getString(3);
                op = new RenameOp(sid, name);
            } else {
                throw new IllegalArgumentException("Operation type " + type + " is not supported");
            }
            return op;
        }
    }

    private PreparedStatement _psGetLeaveCommands;
    public IDBIterator<ISharedFolderOp> getCommands_() throws SQLException
    {
        try {
            if (_psGetLeaveCommands == null) {
                _psGetLeaveCommands = c().prepareStatement(DBUtil.select(T_SPQ, C_SPQ_SID,
                        C_SPQ_TYPE, C_SPQ_NAME) +
                        " order by " + C_SPQ_IDX);
            }
            ResultSet rs = _psGetLeaveCommands.executeQuery();
            return new SharedFolderOpIterator(rs);
        } catch (SQLException e) {
            DBUtil.close(_psGetLeaveCommands);
            _psGetLeaveCommands = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAddLeaveCommand;
    public void addCommand_(ISharedFolderOp op, Trans t) throws SQLException

    {
        try {
            if (_psAddLeaveCommand == null) {
                _psAddLeaveCommand = c().prepareStatement(DBUtil.insert(T_SPQ, C_SPQ_SID,
                        C_SPQ_TYPE, C_SPQ_NAME));
            }
            _psAddLeaveCommand.setBytes(1, op.getSID().getBytes());
            _psAddLeaveCommand.setInt(2, op.getType().getValue());
            if (op instanceof RenameOp){
                _psAddLeaveCommand.setString(3, ((RenameOp)op).getName());
            } else {
                _psAddLeaveCommand.setNull(3, Types.INTEGER);
            }
            int rows = _psAddLeaveCommand.executeUpdate();
            assert rows == 1;
        } catch (SQLException e) {
            DBUtil.close(_psAddLeaveCommand);
            _psAddLeaveCommand = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psRemoveLeaveCommand;
    public void removeCommands_(SID sid, SharedFolderOpType type, Trans t) throws SQLException
    {
        try {
            if (_psRemoveLeaveCommand == null) {
                _psRemoveLeaveCommand = c().prepareStatement(
                        DBUtil.deleteWhere(T_SPQ, C_SPQ_SID + "=? AND " + C_SPQ_TYPE + "=?"));
            }
            _psRemoveLeaveCommand.setBytes(1, sid.getBytes());
            _psRemoveLeaveCommand.setInt(2, type.getValue());
            _psRemoveLeaveCommand.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psRemoveLeaveCommand);
            _psRemoveLeaveCommand = null;
            throw detectCorruption(e);
        }
    }
}
