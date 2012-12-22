package com.aerofs.base.ex;

public class ExFormatError extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExFormatError(String msg)
    {
        super(msg);
    }

    public ExFormatError(Exception e)
    {
        super(e);
    }

}
