/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Exceptions
{
    private static final Logger l = Loggers.getLogger(Exceptions.class);

    private final static Map<Type, Class<? extends AbstractExWirable>> _types = Maps.newHashMap();
    static {
        // Initialize the map with the base types
        // N.B. these exception types will be accessible to Android clients
        _types.put(Type.ALREADY_EXIST, ExAlreadyExist.class);
        _types.put(Type.EMPTY_EMAIL_ADDRESS, ExEmptyEmailAddress.class);
        _types.put(Type.BAD_ARGS, ExBadArgs.class);
        _types.put(Type.NO_PERM, ExNoPerm.class);
        _types.put(Type.NO_RESOURCE, ExNoResource.class);
        _types.put(Type.NOT_FOUND, ExNotFound.class);
        _types.put(Type.PROTOCOL_ERROR, ExProtocolError.class);
        _types.put(Type.TIMEOUT, ExTimeout.class);
        _types.put(Type.BAD_CREDENTIAL, ExBadCredential.class);
        _types.put(Type.INTERNAL_ERROR, ExInternalError.class);
        _types.put(Type.EXTERNAL_SERVICE_UNAVAILABLE, ExExternalServiceUnavailable.class);
        _types.put(Type.CANNOT_RESET_PASSWORD, ExCannotResetPassword.class);
        _types.put(Type.EXTERNAL_AUTH_FAILURE, ExExternalAuthFailure.class);
        _types.put(Type.LICENSE_LIMIT, ExLicenseLimit.class);
        _types.put(Type.RATE_LIMIT_EXCEEDED, ExRateLimitExceeded.class);
        _types.put(Type.SECOND_FACTOR_REQUIRED, ExSecondFactorRequired.class);
        _types.put(Type.WRONG_ORGANIZATION, ExWrongOrganization.class);
        _types.put(Type.NOT_LOCALLY_MANAGED, ExNotLocallyManaged.class);
    }

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
        // Convert InvalidProtocolBufferException to ExProtocolError so that we have a specific
        // type for them rather than ExInternalError
        if (e instanceof InvalidProtocolBufferException) e = new ExProtocolError(e.getMessage());

        Type type;
        byte[] data;
        if (e instanceof AbstractExWirable) {
            type = ((AbstractExWirable)e).getWireType();
            data = ((AbstractExWirable)e).getDataNullable();
        } else {
            type = Type.INTERNAL_ERROR;
            data = null;
        }

        PBException.Builder bd = PBException.newBuilder().setType(type);

        if (e instanceof IExObfuscated) {
            // If this Exception is obfuscated, encode the plain text message
            bd.setPlainTextMessageDeprecated(((IExObfuscated)e).getPlainTextMessage());
        }

        String message = e.getLocalizedMessage();
        if (message != null) bd.setMessageDeprecated(message);
        if (data != null) bd.setData(ByteString.copyFrom(data));
        if (stackTrace) bd.setStackTrace(getStackTraceAsString(Throwables.getRootCause(e)));
        return bd.build();
    }

    public static AbstractExWirable fromPB(PBException pb)
    {
        Type type = pb.getType();
        Class<? extends AbstractExWirable> exClass = _types.get(type);

        if (exClass == null) {
            l.warn("No mapping registered for exception type {} - {}", type.getNumber(), type);
            PBException internal = PBException.newBuilder(pb).setType(Type.INTERNAL_ERROR).build();
            return new ExInternalError(internal);
        }

        try {
            // Note: Class.getConstructor() returns only public constructors, so we have to use
            // getDeclaredConstructor() as some exceptions may have package-private constructors
            return exClass.getDeclaredConstructor(PBException.class).newInstance(pb);
        } catch (Exception e) {
            l.error("Unable to create a new instance for exception type {} - {}",
                    type.getNumber(), type);
            throw Throwables.propagate(e);
        }
    }

    /**
     * Register additional mappings between PBException types and actual exception classes
     * @throws IllegalArgumentException if some types are already registered
     */
    public static void registerExceptionTypes(Map<Type, Class<? extends AbstractExWirable>> types)
    {
        // Check if some types are already registered
        // Note (GS): This is not formally needed, it is just here as a safeguard against potential
        // programming mistakes. Feel free to lift this requirement in the future if it turns out
        // to be useful
        Set<Type> intersection = new HashSet<Type>(_types.keySet());
        intersection.retainAll(types.keySet());
        if (!intersection.isEmpty()) {
            throw new IllegalArgumentException("Cannot register exception type twice: " +
                    Arrays.toString(intersection.toArray()));
        }

        _types.putAll(types);
    }

    public static String getStackTraceAsString(Throwable e)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, false);
        AbstractExWirable.printStackTrace(e, pw);
        return sw.toString();
    }

    /**
     * This method returns a hash string that should remain identical for multiple instances of the
     * same exception, even if the exception message is different.
     */
    public static String getChecksum(Throwable e)
    {
        // Compute the checksum by md5'ing the class exception name as well as the stack trace
        // note: getBytes() can return anything in any encoding, don't make assumptions. But it will
        // be consistent within a platform, and that's what matters.
        // (In other words: if we have classes with unicode chararecters in the names, then the
        // checksum may vary across paltforms)
        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        for (Throwable t : Throwables.getCausalChain(e)) {
            md.update(t.getClass().getName().getBytes());
            for (StackTraceElement el : t.getStackTrace()) {
                md.update(el.getClassName().getBytes());
                md.update(el.getMethodName().getBytes());
                md.update(Integer.toString(el.getLineNumber()).getBytes());
            }
        }
        return BaseUtil.hexEncode(md.digest());
    }
}
