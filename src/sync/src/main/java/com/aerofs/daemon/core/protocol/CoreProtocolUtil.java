package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;

public abstract class CoreProtocolUtil
{
    // FIXME (AG): deprecated - all core protocol messages will have a message id
    // if a response is generated to a message that response will have the message id of the request
    // in this case there is no NOT_RPC message id
    public static final int NOT_RPC = PBCore.getDefaultInstance().getRpcid();

    private static int s_rpcid = 0;

    private CoreProtocolUtil()
    {
        // private to prevent instantiation
    }

    public static PBCore.Builder newRequest(Type type)
    {
        return newCoreMessage(type).setRpcid(nextRPCID_());
    }

    private static int nextRPCID_()
    {
        if (++s_rpcid == NOT_RPC) ++s_rpcid;
        return s_rpcid;
    }

    public static PBCore.Builder newCoreMessage(Type type)
    {
        return PBCore.newBuilder().setType(type);
    }

    public static PBCore.Builder newResponse(PBCore request)
            throws ExProtocolError
    {
        if (!request.hasRpcid()) {
            throw new ExProtocolError("missing rpcid");
        }

        return newResponse(request.getRpcid());
    }

    public static PBCore.Builder newResponse(int rpcid)
    {
        return PBCore
                .newBuilder()
                .setType(Type.REPLY)
                .setRpcid(rpcid);
    }

    public static PBCore newErrorResponse(PBCore request, Throwable cause)
            throws ExProtocolError
    {
        // use toPBWithStackTrace to ease debugging.
        // FIXME (WW): change toPBWithStackTrace back to toPB() for security concerns

        return newResponse(request)
                .setExceptionResponse(Exceptions.toPBWithStackTrace(cause))
                .build();
    }

    public static String typeString(PBCore message)
    {
        String typeString = typeString(message.getType());
        return message.hasExceptionResponse() ? typeString + ":E" + exceptionTypeString(message.getExceptionResponse().getType()) : typeString;
    }

    private static String typeString(PBCore.Type type)
    {
        return Integer.toString(type.getNumber());
    }

    private static String exceptionTypeString(PBException.Type type)
    {
        return Integer.toString(type.getNumber());
    }
}
