package com.aerofs.baseline.simple.api;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Java representation of Customer object.
 * <p/>
 * Has the following fields:
 * <ul>
 *     <li>customer_id</li>
 *     <li>customer_name</li>
 *     <li>organization_name</li>
 *     <li>seats</li>
 * </ul>
 * Note that {@code customer_id} is <strong>not</strong>
 * set in requests.
 */
public final class Customer {

    public int customerId = 0; // not set when submitted by user

    @NotNull
    public String customerName;

    @NotNull
    public String organizationName;

    @Min(1)
    public int seats;

    @SuppressWarnings("unused")
    private Customer() { } // do not use - only for Jackson

    public Customer(int customerId, String customerName, String organizationName, int seats) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.organizationName = organizationName;
        this.seats = seats;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Customer other = (Customer) o;

        return customerId == other.customerId
                && Objects.equal(customerName, other.customerName)
                && Objects.equal(organizationName, other.organizationName)
                && seats == other.seats;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(customerId, customerName, organizationName, seats);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("customerId", customerId)
                .add("customerName", customerName)
                .add("organizationName", organizationName)
                .add("seats", seats)
                .toString();
    }
}
