package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException.Type;

public class ExEmailAborted extends AbstractExWirable {

    private static final long serialVersionUID = 1L;

    public ExEmailAborted(String s) {
        super(s);
    }

    @Override
    public Type getWireType() {
        return Type.INTERNAL_ERROR;
    }
}
