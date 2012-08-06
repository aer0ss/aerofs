package com.aerofs.daemon.core;

import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;

public class CoreUtil
{
    public static final int NOT_RPC = PBCore.getDefaultInstance().getRpcid();

//    private static final boolean OBFUSCATED = !"REPLY".equals(PBCore.Type.REPLY.name());
    private static final boolean OBFUSCATED = true;

    private static int s_id = 0;

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

    private static String typeString(PBCore.Type type)
    {
        String str = Integer.toString(type.getNumber());
        if (!OBFUSCATED) str += '(' + type.name() + ')';
        return str;
    }

    private static String exceptionTypeString(PBException.Type type)
    {
        String str = Integer.toString(type.getNumber());
        if (!OBFUSCATED) str += '(' + type.name() + ')';
        return str;
    }

    public static String typeString(PBCore pb)
    {
        String typeString = typeString(pb.getType());
        return pb.hasExceptionReply() ?
            typeString + ":E" + exceptionTypeString(pb.getExceptionReply().getType()) :
            typeString;
    }
}
