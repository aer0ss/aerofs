package com.aerofs.daemon.core;

import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;

public abstract class CoreUtil
{
    public static final int NOT_RPC = PBCore.getDefaultInstance().getRpcid();

    private static int s_id = 0;

    private CoreUtil()
    {
        // private to prevent instantiation
    }

    private static int nextRPCID_()
    {
        if (++s_id == NOT_RPC) ++s_id;
        return s_id;
    }

    public static PBCore.Builder newCore(Type type)
    {
        return PBCore.newBuilder().setType(type);
    }

    public static PBCore.Builder newCall(Type type)
    {
        return newCore(type).setRpcid(nextRPCID_());
    }

    public static PBCore.Builder newReply(int rpcid)
    {
        return PBCore.newBuilder()
            .setType(Type.REPLY)
            .setRpcid(rpcid);
    }

    public static PBCore.Builder newReply(PBCore call) throws ExProtocolError
    {
        if (!call.hasRpcid()) throw new ExProtocolError("missing rpcid");
        return newReply(call.getRpcid());
    }

    public static PBCore newErrorReply(PBCore msg, Throwable cause)
            throws ExProtocolError
    {
        // use toPBWithStackTrace to ease debugging.
        // FIXME (WW): change toPBWithStackTrace back to toPB() for security concerns

        return newReply(msg)
                .setExceptionReply(Exceptions.toPBWithStackTrace(cause))
                .build();
    }

    public static String typeString(PBCore pb)
    {
        String typeString = typeString(pb.getType());
        return pb.hasExceptionReply() ?
            typeString + ":E" + exceptionTypeString(pb.getExceptionReply().getType()) : typeString;
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
