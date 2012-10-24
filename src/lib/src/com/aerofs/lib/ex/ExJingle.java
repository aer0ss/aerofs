package com.aerofs.lib.ex;

public class ExJingle extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExJingle(String msg)
    {
        super(msg);
    }

    @Override
    public String toString()
    {
        return ExJingle.class.getSimpleName() + ":" + getMessage();
    }
}
