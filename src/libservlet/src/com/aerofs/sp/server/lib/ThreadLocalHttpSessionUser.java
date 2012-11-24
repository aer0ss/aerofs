package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.user.ISessionUser;
import org.apache.log4j.Logger;

/**
 * Wraps a ThreadLocal HttpSession and provides a getter, setter, and remover of the 'user'
 * attribute.
 *
 * TODO (WW) FIXME The correlation of sessions and threads are accidental. It is incorrect to use
 * thread-local storage to simulate session-local storage.
 */
public class ThreadLocalHttpSessionUser
        extends AbstractThreadLocalHttpSession
        implements ISessionUser
{
    private static final Logger l = Util.l(ThreadLocalHttpSessionUser.class);
    private static final String SESS_ATTR_USER  = "user";

    @Override
    public UserID getID() throws ExNoPerm
    {
        UserID userId = (UserID) _session.get().getAttribute(SESS_ATTR_USER);
        if (userId == null) {
            l.info("not authenticated: session " + _session.get().getId());
            throw new ExNoPerm();
        }

        return userId;
    }

    @Override
    public void setID(UserID userId)
    {
        _session.get().setAttribute(SESS_ATTR_USER, userId);
    }

    @Override
    public void remove()
    {
        _session.get().removeAttribute(SESS_ATTR_USER);
    }
}
