/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.sp.server.settings.token;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.sp.server.lib.user.User;

import javax.inject.Inject;
import java.sql.SQLException;

/**
 * The user settings token is the token displayed on the user settings page.
 *
 * The user can optionally create this token. Such tokens are intended to facilitate ease of use
 * with API-enabled applications that do not support the full OAuth authentication flow. In such
 * cases, it is convenient to create a token, supply it to an application and allow that
 * application to access your AeroFS.
 *
 * This class represents such tokens.
 */
public class UserSettingsToken
{
    public static class Factory
    {
        private UserSettingsTokenDatabase _db;

        @Inject
        public void inject(UserSettingsTokenDatabase db)
        {
            _db = db;
        }

        public UserSettingsToken create(User user)
                throws ExNoPerm
        {
            return new UserSettingsToken(this, user);
        }

        public UserSettingsToken save(User user, String token)
                throws SQLException, ExAlreadyExist
        {
            _db.insertToken(user.id(), token);
            return new UserSettingsToken(this, user);
        }
    }

    private final Factory _f;
    private final User _user;

    private UserSettingsToken(Factory f, User user)
    {
        _f = f;
        _user = user;
    }

    public User getUser()
    {
        return _user;
    }

    public boolean exists()
            throws SQLException
    {
        return _f._db.hasToken(_user.id());
    }

    public String get()
            throws SQLException, ExNotFound
    {
        return _f._db.getToken(_user.id());
    }

    public void delete()
            throws SQLException
    {
        _f._db.deleteToken(_user.id());
    }
}
