package com.aerofs.daemon.event.lib.imc;

import com.aerofs.daemon.event.IEBIMC;
import com.aerofs.lib.event.Prio;

// N.B. core code must use CoreIMC.execute() etc
//
abstract public class AbstractEBIMC implements IEBIMC {

    private IIMCExecutor _imce;
    private Exception _e;

    protected AbstractEBIMC(IIMCExecutor imce)
    {
        _imce = imce;
    }

    @Override
    public void okay()
    {
        _imce.done_(this);
    }

    @Override
    public void error(Exception e)
    {
        _e = e;
        _imce.done_(this);
    }

    public void execute(Prio prio) throws Exception
    {
        _imce.execute_(this, prio);
        if (_e != null) throw _e;
    }

//    public Exception executeNoThrows(Prio prio)
//    {
//        _imce.execute(this, prio);
//        if (_e != null) l.warn("execIE: " + Util.e(_e));
//        return _e;
//    }

    public void enqueueBlocking(Prio prio)
    {
        _imce.enqueueBlocking_(this, prio);
    }

    public IIMCExecutor imce()
    {
        return _imce;
    }

    public Exception exception()
    {
        return _e;
    }
}
