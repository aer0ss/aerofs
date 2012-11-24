package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.user.ISessionUser;
import com.aerofs.sp.server.lib.user.User;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;

/**
 * Wraps a ThreadLocal HttpSession and provides a getter, setter, and remover of the 'user'
 * attribute.
 */
public class ThreadLocalHttpSessionUser
        extends AbstractThreadLocalHttpSession
        implements ISessionUser
{
    private static final Logger l = Util.l(ThreadLocalHttpSessionUser.class);
    private static final String SESS_ATTR_USER  = "user";

    @Override
    public @Nonnull UserID getID() throws ExNoPerm
    {
        return get().id();
    }

    @Override
    public @Nonnull User get() throws ExNoPerm
    {
        User user = (User) _session.get().getAttribute(SESS_ATTR_USER);
        if (user == null) {
            l.info("not authenticated: session " + _session.get().getId());
            throw new ExNoPerm();
        } else {
            return user;
        }
    }

    @Override
    public void set(@Nonnull User user)
    {
        _session.get().setAttribute(SESS_ATTR_USER, user);
    }

    @Override
    public void remove()
    {
        _session.get().removeAttribute(SESS_ATTR_USER);
    }
}
