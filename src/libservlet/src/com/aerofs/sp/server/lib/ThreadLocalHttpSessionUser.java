package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.sp.server.lib.user.ISessionUserID;
import org.apache.log4j.Logger;

/**
 * Wraps a ThreadLocal HttpSession and provides a getter, setter, and remover of the 'user'
 * attribute.
 */
public class ThreadLocalHttpSessionUser
        extends AbstractThreadLocalHttpSession
        implements ISessionUserID
{
    private static final Logger l = Util.l(ThreadLocalHttpSessionUser.class);

    @Override
    public String getUser() throws ExNoPerm
    {
        String user = (String) _session.get().getAttribute(SPParam.SESS_ATTR_USER);
        if (user == null) {
            l.info("not authenticated: session " + _session.get().getId());
            throw new ExNoPerm();
        }

        return user;
    }

    @Override
    public void setUser(String userId)
    {
        _session.get().setAttribute(SPParam.SESS_ATTR_USER, userId);
    }

    @Override
    public void removeUser()
    {
        _session.get().removeAttribute(SPParam.SESS_ATTR_USER);
    }
}
