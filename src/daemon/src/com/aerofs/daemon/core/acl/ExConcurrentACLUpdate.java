package com.aerofs.daemon.core.acl;

public class ExConcurrentACLUpdate extends Exception
{
    private static final long serialVersionUID = 1L;

    private final long _expectedLocalACL;
    private final long _actualLocalACL;

    public ExConcurrentACLUpdate(long expectedLocalACL, long actualLocalACL)
    {
        super("concurrent acl update during server sync " +
                "exp:" + expectedLocalACL + " act:" + actualLocalACL);
        _expectedLocalACL = expectedLocalACL;
        _actualLocalACL = actualLocalACL;
    }

    public long getExpectedLocalACL()
    {
        return _expectedLocalACL;
    }

    public long getActualLocalACL()
    {
        return _actualLocalACL;
    }
}
