package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class Exceptions
{
    /**
     * Convert a Throwable to PBException with stack trace disabled for security or performance
     * reasons. Stack traces from all the previous exception wiring on the same cascading chain will
     * be lost. See AbstractExWirable for detail.
     */
    public static PBException toPB(Throwable e)
    {
        return toPBImpl(e, false);
    }

    /**
     * Convert a Throwable to PBException with stack trace
     */
    public static PBException toPBWithStackTrace(Throwable e)
    {
        return toPBImpl(e, true);
    }

    private static PBException toPBImpl(Throwable e, boolean stackTrace)
    {
        Type type;
        if (e instanceof AbstractExWirable) {
            type = ((AbstractExWirable) e).getWireType();
        } else {
            type = Type.INTERNAL_ERROR;
        }

        Throwable eRoot = e;
        while (eRoot.getCause() != null) eRoot = eRoot.getCause();

        PBException.Builder bd = PBException.newBuilder().setType(type);

        if (e instanceof IExObfuscated) {
            // If this Exception is obfuscated, encode the plain text message
            bd.setPlainTextMessage(((IExObfuscated) e).getPlainTextMessage());
        }

        String message = e.getLocalizedMessage();
        if (message != null) bd.setMessage(message);
        if (stackTrace) bd.setStackTrace(Util.stackTrace2string(eRoot));
        return bd.build();
    }

    public static AbstractExWirable fromPB(PBException pb)
    {
        return fromPBImpl(pb);
    }

    private static AbstractExWirable fromPBImpl(PBException pb)
    {
        switch (pb.getType()) {
        case INTERNAL_ERROR:        return new ExInternalError(pb);
        case ABORTED:               return new ExAborted(pb);
        case ALREADY_EXIST:         return new ExAlreadyExist(pb);
        case PARENT_ALREADY_SHARED: return new ExParentAlreadyShared(pb);
        case CHILD_ALREADY_SHARED:  return new ExChildAlreadyShared(pb);
        case BAD_ARGS:              return new ExBadArgs(pb);
        case NO_PERM:               return new ExNoPerm(pb);
        case NO_RESOURCE:           return new ExNoResource(pb);
        case NOT_DIR:               return new ExNotDir(pb);
        case NOT_FILE:              return new ExNotFile(pb);
        case NOT_FOUND:             return new ExNotFound(pb);
        case OUT_OF_SPACE:          return new ExOutOfSpace(pb);
        case PROTOCOL_ERROR:        return new ExProtocolError(pb);
        case TIMEOUT:               return new ExTimeout(pb);
        case DEVICE_OFFLINE:        return new ExDeviceOffline(pb);
        case UPDATE_IN_PROGRESS:    return new ExUpdateInProgress(pb);
        case NO_AVAIL_DEVICE:       return new ExNoAvailDevice(pb);
        case NOT_SHARED:            return new ExNotShared(pb);
        case BAD_CREDENTIAL:        return new ExBadCredential(pb);
        case EXCLUDED:              return new ExExpelled(pb);
        case UI_MESSAGE:            return new ExUIMessage(pb);
        case DEVICE_ID_ALREADY_EXISTS:
                                    return new ExDeviceIDAlreadyExists(pb);
        case NO_COMPONENT_WITH_SPECIFIED_VERSION:
                                    return new ExNoComponentWithSpecifiedVersion(pb);
        case INDEXING:              return new ExIndexing(pb);
        default: assert false : "unsupported PBException type"; return null;
        }
    }
}
