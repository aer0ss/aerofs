package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

@JsonInclude( Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class Invoice extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( DATE_KEY ) private final long date;
    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( CUSTOMER_KEY ) private final String customerId;
    @JsonProperty( PERIOD_START_KEY ) private final long periodStart;
    @JsonProperty( PERIOD_END_KEY ) private final long periodEnd;
    @JsonProperty( LINES_KEY ) private final StripeIterable<LineItem> lines;
    @JsonProperty( CHARGE_KEY ) private final Optional<String> charge;
    @JsonProperty( STARTING_BALANCE_KEY ) private final int startingBalance;
    @JsonProperty( ENDING_BALANCE_KEY ) private final Optional<Integer> endingBalance;
    @JsonProperty( DISCOUNT_KEY ) private final Optional<Discount> discount;
    @JsonProperty( SUBTOTAL_KEY ) private final int subtotal;
    @JsonProperty( TOTAL_KEY ) private final int total;
    @JsonProperty( AMOUNT_DUE_KEY ) private final int amountDue;
    @JsonProperty( CURRENCY_KEY ) private final String currency;
    @JsonProperty( PAID_KEY ) private final boolean paid;
    @JsonProperty( CLOSED_KEY ) private final boolean closed;
    @JsonProperty( ATTEMPTED_KEY ) private final boolean attempted;
    @JsonProperty( ATTEMPT_COUNT_KEY ) private final int attemptCount;
    @JsonProperty( NEXT_PAYMENT_ATTEMPT_KEY ) private final Optional<Long> nextPaymentAttempt;

    @JsonCreator
    private Invoice( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( DATE_KEY ) final long date,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( CUSTOMER_KEY ) final String customerId,
            @JsonProperty( PERIOD_START_KEY ) final long periodStart,
            @JsonProperty( PERIOD_END_KEY ) final long periodEnd,
            @JsonProperty( LINES_KEY ) final StripeIterable<LineItem> lines,
            @JsonProperty( CHARGE_KEY ) final Optional<String> charge,
            @JsonProperty( STARTING_BALANCE_KEY ) final int startingBalance,
            @JsonProperty( ENDING_BALANCE_KEY ) final Optional<Integer> endingBalance,
            @JsonProperty( DISCOUNT_KEY ) final Optional<Discount> discount,
            @JsonProperty( SUBTOTAL_KEY ) final int subtotal,
            @JsonProperty( TOTAL_KEY ) final int total,
            @JsonProperty( AMOUNT_DUE_KEY ) final int amountDue,
            @JsonProperty( CURRENCY_KEY ) final String currency,
            @JsonProperty( PAID_KEY ) final boolean paid,
            @JsonProperty( CLOSED_KEY ) final boolean closed,
            @JsonProperty( ATTEMPTED_KEY ) final boolean attempted,
            @JsonProperty( ATTEMPT_COUNT_KEY ) final int attemptCount,
            @JsonProperty( NEXT_PAYMENT_ATTEMPT_KEY ) final Optional<Long> nextPaymentAttempt ) {
        super( id, STRIPE_TYPE );
        this.livemode = livemode;
        this.amountDue = amountDue;
        this.attemptCount = attemptCount;
        this.attempted = attempted;
        this.closed = closed;
        this.currency = currency;
        this.customerId = customerId;
        this.date = date;
        this.lines = lines;
        this.paid = paid;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.startingBalance = startingBalance;
        this.subtotal = subtotal;
        this.total = total;
        this.charge = charge;
        this.discount = discount;
        this.endingBalance = endingBalance;
        this.nextPaymentAttempt = nextPaymentAttempt;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( DATE_KEY, getDate() )
                .add( LIVEMODE_KEY, isLivemode() )
                .add( CUSTOMER_KEY, getCustomerId() )
                .add( PERIOD_START_KEY, getPeriodStart() )
                .add( PERIOD_END_KEY, getPeriodEnd() )
                .add( LINES_KEY, getLines() )
                .add( CHARGE_KEY, getCharge() )
                .add( STARTING_BALANCE_KEY, getStartingBalance() )
                .add( ENDING_BALANCE_KEY, getEndingBalance() )
                .add( DISCOUNT_KEY, getDiscount() )
                .add( SUBTOTAL_KEY, getSubtotal() )
                .add( TOTAL_KEY, getTotal() )
                .add( AMOUNT_DUE_KEY, getAmountDue() )
                .add( CURRENCY_KEY, getCurrency() )
                .add( PAID_KEY, isPaid() )
                .add( CLOSED_KEY, isClosed() )
                .add( ATTEMPTED_KEY, isAttempted() )
                .add( ATTEMPT_COUNT_KEY, getAttemptCount() )
                .add( NEXT_PAYMENT_ATTEMPT_KEY, getNextPaymentAttempt() )
                .toString();
    }

    public boolean isLivemode() {
        return livemode;
    }

    public int getAmountDue() {
        return amountDue;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public boolean isAttempted() {
        return attempted;
    }

    public boolean isClosed() {
        return closed;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCustomerId() {
        return customerId;
    }

    public long getDate() {
        return date;
    }

    public Iterable<LineItem> getLines() {
        return lines;
    }

    public boolean isPaid() {
        return paid;
    }

    public long getPeriodStart() {
        return periodStart;
    }

    public long getPeriodEnd() {
        return periodEnd;
    }

    public int getStartingBalance() {
        return startingBalance;
    }

    public int getSubtotal() {
        return subtotal;
    }

    public int getTotal() {
        return total;
    }

    public Optional<String> getCharge() {
        return charge;
    }

    public Optional<Discount> getDiscount() {
        return discount;
    }

    public Optional<Integer> getEndingBalance() {
        return endingBalance;
    }

    public Optional<Long> getNextPaymentAttempt() {
        return nextPaymentAttempt;
    }

    private static final String DATE_KEY = "date";
    private static final String PERIOD_START_KEY = "period_start";
    private static final String PERIOD_END_KEY = "period_end";
    private static final String SUBTOTAL_KEY = "subtotal";
    private static final String TOTAL_KEY = "total";
    private static final String CUSTOMER_KEY = "customer";
    private static final String ATTEMPTED_KEY = "attempted";
    private static final String CLOSED_KEY = "closed";
    private static final String PAID_KEY = "paid";
    private static final String ATTEMPT_COUNT_KEY = "attempt_count";
    private static final String AMOUNT_DUE_KEY = "amount_due";
    private static final String CURRENCY_KEY = "currency";
    private static final String STARTING_BALANCE_KEY = "starting_balance";
    private static final String ENDING_BALANCE_KEY = "ending_balance";
    private static final String NEXT_PAYMENT_ATTEMPT_KEY = "next_payment_attempt";
    private static final String CHARGE_KEY = "charge";
    private static final String DISCOUNT_KEY = "discount";
    private static final String LINES_KEY = "lines";
    public static final String STRIPE_TYPE = "invoice";

}
