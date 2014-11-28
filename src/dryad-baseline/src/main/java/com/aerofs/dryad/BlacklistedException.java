package com.aerofs.dryad;

public final class BlacklistedException extends Exception {

    private static final long serialVersionUID = -1977255426619516766L;

    public BlacklistedException(String forbiddenEntity) {
        super(forbiddenEntity + " is forbidden accessing this service");
    }
}
