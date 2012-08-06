package com.aerofs.lib.ex;

public class ExLimitMonitor extends Exception
{
    private static final long serialVersionUID = 1;

    public ExLimitMonitor(String reason)
    {
        super(reason);
    }
}
