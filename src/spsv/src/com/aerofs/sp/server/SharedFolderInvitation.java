/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.server.lib.SharedFolderInvitationDatabase;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class SharedFolderInvitation
{
    public static class Factory
    {
        private final SharedFolderInvitationDatabase _db;
        private final User.Factory _factUser;
        private final SharedFolder.Factory _factSharedFolder;

        @Inject
        public Factory(SharedFolderInvitationDatabase db, User.Factory factUser,
                SharedFolder.Factory factSharedFolder)
        {
            _db = db;
            _factUser = factUser;
            _factSharedFolder = factSharedFolder;
        }

        /**
         * Return a SharedFolderInvitation object with a newly genearted code.
         */
        public SharedFolderInvitation createWithGeneratedCode()
        {
            return create(InvitationCode.generate(CodeType.SHARE_FOLDER));
        }

        public SharedFolderInvitation create(String code)
        {
            return new SharedFolderInvitation(this, code);
        }

        /**
         * Return all the invitations for the given user. At most one invitation is returned
         * for each shared folder.
         */
        public Collection<SharedFolderInvitation> listAll(User sharee)
                throws SQLException
        {
            Collection<String> codes = _db.getAll(sharee.id());
            List<SharedFolderInvitation> sfis = Lists.newArrayListWithCapacity(codes.size());
            for (String code : codes) sfis.add(create(code));
            return sfis;
        }
    }

    private final String _code;
    private final Factory _f;

    public SharedFolderInvitation(Factory f, String code)
    {
        _f = f;
        _code = code;
    }

    String code()
    {
        return _code;
    }

    @Override
    public int hashCode()
    {
        return _code.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return o == this || (o != null && ((SharedFolderInvitation)o)._code.equals(_code));
    }

    @Override
    public String toString()
    {
        return "shared folder invitation " + _code;
    }

    /**
     * Add the invitation to the database
     */
    void add(User sharer, User sharee, SharedFolder sf, Role role, String folderName)
            throws SQLException
    {
        _f._db.add(sharer.id(), sharee.id(), sf.id(), folderName, role, _code);
    }

    User getSharee()
            throws ExNotFound, SQLException
    {
        return _f._factUser.create(_f._db.getSharee(_code));
    }

    User getSharer()
            throws ExNotFound, SQLException
    {
        return _f._factUser.create(_f._db.getSharer(_code));
    }

    /**
     * TODO (WW) use getSharedFolder().getName() instead.
     */
    String getFolderName()
            throws ExNotFound, SQLException
    {
        return _f._db.getFolderName(_code);
    }

    SharedFolder getSharedFolder()
            throws ExNotFound, SQLException
    {
        return _f._factSharedFolder.create_(_f._db.getSID(_code));
    }

    Role getRole()
            throws ExNotFound, SQLException
    {
        return _f._db.getRole(_code);
    }
}
