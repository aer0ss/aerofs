package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * This is a base class for all the exceptions that have a corresponding type in PBException, and
 * thus can be sent over the wire. When a sender sends a wirable exception, it converts the
 * exception to a PBException message. On receiving the message, the receiver converts it back to an
 * exception of the same type. All non-wirable exceptions will be converted to ExInternalError on
 * the receiver side. We call this process _exception wiring_.
 *
 * Exception wiring may be cascaded in a multi-tier system. A typical example is when the UI
 * Controller receives an exception from Ritual, it forwards the exception to the UI Viewer. All
 * the three components talk to each other via protobuf message exchange. The following code
 * simulates the situation:
 *
 *      // process A generates an exception
 *      Exception e1 = new ExNotFound("foo");
 *      PBException pb1 = Exceptions.toPBWithStackTrace(e1);
 *      // send pb1 to process B...
 *
 *      // ... process B receives pb1
 *      Exception e2 = Exceptions.fromPB(pb1);
 *      assert e2 instanceof ExNotFound;
 *      PBException pb2 = Exceptions.toPBWithStackTrace(e2);
 *      // send pb2 to process C...
 *
 *      // ... process C receives pb2
 *      Exception e3 = Exceptions.fromPB(pb2);
 *      assert e3 instanceof ExNotFound;
 *
 * The last exception contains all the previous stack traces in the chain, such that
 *
 *      System.out.println(e3.getRemoteStackTrace());
 *
 * would print:
 *
 *      com.aerofs.lib.ex.remote.ExRemoteNotFound: foo
 *          at ... (stack trace in process C)
 *      Remote stack:
 *      com.aerofs.lib.ex.remote.ExRemoteNotFound: foo
 *          at ... (stack trace in process B)
 *      Remote stack:
 *      com.aerofs.lib.ex.ExNotFound: foo
 *          at ... (stack trace in process C)
 *
 * ----
 *
 * Rationale:
 *
 * "Wirable" is different from "serializable" in that when a wirable exception A is wired into B,
 * more information is added to B to include A's stack trace, whereas "serialization" reproduces
 * exactly the same objects. In addition, in exception wiring, all non-wirable exceptions are wired
 * into ExInternalError.
 */
public abstract class AbstractExWirable extends Exception
{
    private static final long serialVersionUID = 1L;

    private final PBException _pb;

    /**
     * @return A PBException.Type value corresponding to this exception, used when transferring the
     * exception over the wire.
     */
    public abstract Type getWireType();

    protected AbstractExWirable()
    {
        super();
        _pb = null;
    }

    protected AbstractExWirable(String msg)
    {
        super(msg);
        _pb = null;
    }

    protected AbstractExWirable(Throwable cause)
    {
        super(cause);
        _pb = null;
    }

    protected AbstractExWirable(String msg, Throwable cause)
    {
        super(msg, cause);
        _pb = null;
    }

    protected AbstractExWirable(PBException pb)
    {
        super(pb.getLocalizedMessage());
        _pb = pb;
        assert getWireType() == pb.getType();
    }

    /**
     * Return string format of the exception's wire type if no message has been set. The method
     * should not extend the message if it's already non-empty. Otherwise, cascaded exception wiring
     * may generate repetitive emssage strings such as "not found: not found: not found: file foo".
     */
    @Override
    public String getMessage()
    {
        String superMessage = super.getMessage();
        if (superMessage == null || superMessage.isEmpty()) return getWireTypeString();
        else return superMessage;
    }

    /**
     * @return a user-friendly representation of the wire type in English. If the wire type of the
     * exception is FOO_BAR, this method returns "foo bar".
     *
     * N.B. The Java obfuscator must retain the wire type
     */
    public String getWireTypeString()
    {
        return getWireType().name().toLowerCase().replace('_', ' ');
    }

    /**
     * @return true iff the object is a result of exception wiring. In other words, iff the object
     * is converted from a PBException message.
     */
    public boolean hasRemoteStackTrace()
    {
        return _pb != null;
    }

    /**
     * @pre hasRemoteStackTrace() returns true
     * @return stack trace on the remote system before the exception is wired. It may contain
     * multiple traces on cascaded exception wiring. Empty if the last remote system doesn't provide
     * the trace.
     */
    public String getRemoteStackTrace()
    {
        assert hasRemoteStackTrace();
        return _pb.getStackTrace();
    }
}
