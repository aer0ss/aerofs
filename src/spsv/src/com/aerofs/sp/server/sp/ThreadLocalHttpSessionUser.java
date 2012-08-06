package com.aerofs.sp.server.sp;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.sp.server.sp.user.ISessionUserID;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpSession;

/**
 * Wraps a ThreadLocal HttpSession and provides a getter, setter,
 * and remover of the 'user' attribute.
 */
public class ThreadLocalHttpSessionUser implements ISessionUserID
{
    private static final Logger l = Util.l(ThreadLocalHttpSessionUser.class);
    protected static final ThreadLocal<HttpSession> _session = new ThreadLocal<HttpSession>();

    @Override
    public String getUser() throws ExNoPerm
    {
        String user = (String) _session.get().getAttribute(SPParam.SESS_ATTR_USER);
        if (user == null) {
            l.info("not authenticated: session " + _session.get().getId());
            throw new ExNoPerm();
        }
        l.info("Session " + _session.get().getId() + " w user " + user);

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

    /**
     * Set the thread-local HttpSession. The only expected caller is SPServlet
     */
    public void setSession(HttpSession httpSession)
    {
        assert httpSession != null;
        _session.set(httpSession);
    }
}
