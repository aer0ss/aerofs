package com.aerofs.customerio;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException( final Throwable cause ) {
        super( cause );
    }

    private static final long serialVersionUID = -8312905205239880833L;

}
