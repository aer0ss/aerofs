package com.aerofs.ids;

/**
 * UniqueID subclasses may restrict the range of valid values. When such IDs are constructed
 * from internal values, asserts should be used to enforce the restrictions. However, when such
 * IDs are received from the outside the network, it should be possible to simply ignore invalid
 * IDs to avoid DoS by remote peers so exceptions should be preferred in that case.
 */
public class ExInvalidID extends Exception
{
    private static final long serialVersionUID = 0L;
    public ExInvalidID() { super(); }
    public ExInvalidID(String reason) { super(reason); }
    public ExInvalidID(Throwable cause) { super(cause); }
}
