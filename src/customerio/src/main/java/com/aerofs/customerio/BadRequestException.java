package com.aerofs.customerio;

public class BadRequestException extends RuntimeException {

    public BadRequestException( final Throwable cause ) {
        super( cause );
    }

    private static final long serialVersionUID = 2265168868354226784L;

}
