/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * Thrown when a user attempts to reset a password for an account that
 * is externally-managed.
 */
public class ExCannotResetPassword extends AbstractExWirable
{
    public ExCannotResetPassword() { super(); }
    public ExCannotResetPassword(PBException pb) { super(pb); }

    @Override
    public Type getWireType() { return Type.CANNOT_RESET_PASSWORD; }

    private static final long serialVersionUID = -1517943457076877083L;
}
