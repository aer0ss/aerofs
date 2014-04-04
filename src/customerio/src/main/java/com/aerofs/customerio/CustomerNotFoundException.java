package com.aerofs.customerio;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException( final Throwable cause ) {
        super( cause );
    }

    private static final long serialVersionUID = 2178574105572288534L;

}
