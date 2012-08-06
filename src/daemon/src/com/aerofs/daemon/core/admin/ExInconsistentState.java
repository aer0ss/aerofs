package com.aerofs.daemon.core.admin;

public class ExInconsistentState extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExInconsistentState(String msg)
    {
        super(msg);
    }

    public ExInconsistentState(Exception e)
    {
        super(e);
    }

}
