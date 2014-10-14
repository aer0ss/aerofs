package com.aerofs.baseline.simple.resources;

/**
 * Thrown when a customer identified by {@code customerId} does not exist.
 */
public final class InvalidCustomerException extends Exception {

    private static final long serialVersionUID = -6460118641963313317L;

    public InvalidCustomerException(int customerId) {
        super("no customer with id " + customerId);
    }
}
