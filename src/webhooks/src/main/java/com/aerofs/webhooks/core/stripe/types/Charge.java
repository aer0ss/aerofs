package com.aerofs.webhooks.core.stripe.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

@JsonInclude( Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class Charge extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( CREATED_KEY ) private final long created;
    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( CUSTOMER_KEY ) private final Optional<String> customer;
    @JsonProperty( DESCRIPTION_KEY ) private final Optional<String> description;
    @JsonProperty( CARD_KEY ) private final Card card;
    @JsonProperty( FEE_KEY ) private final int fee;
    @JsonProperty( FEE_DETAILS_KEY ) private final ImmutableList<FeeDetail> feeDetails;
    @JsonProperty( AMOUNT_KEY ) private final int amount;
    @JsonProperty( CURRENCY_KEY ) private final String currency;
    @JsonProperty( PAID_KEY ) private final boolean paid;
    @JsonProperty( REFUNDED_KEY ) private final boolean refunded;
    @JsonProperty( AMOUNT_REFUNDED_KEY ) private final int amountRefunded;
    @JsonProperty( DISPUTE_KEY ) private final Optional<Dispute> dispute;
    @JsonProperty( FAILURE_MESSAGE_KEY ) private final Optional<String> failureMessage;

    @JsonCreator
    private Charge( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( CREATED_KEY ) final long created,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( CUSTOMER_KEY ) final Optional<String> customer,
            @JsonProperty( DESCRIPTION_KEY ) final Optional<String> description,
            @JsonProperty( CARD_KEY ) final Card card,
            @JsonProperty( FEE_KEY ) final int fee,
            @JsonProperty( FEE_DETAILS_KEY ) final List<FeeDetail> feeDetails,
            @JsonProperty( AMOUNT_KEY ) final int amount,
            @JsonProperty( CURRENCY_KEY ) final String currency,
            @JsonProperty( PAID_KEY ) final boolean paid,
            @JsonProperty( REFUNDED_KEY ) final boolean refunded,
            @JsonProperty( AMOUNT_REFUNDED_KEY ) final int amountRefunded,
            @JsonProperty( DISPUTE_KEY ) final Optional<Dispute> dispute,
            @JsonProperty( FAILURE_MESSAGE_KEY ) final Optional<String> failureMessage ) {
        super( id, STRIPE_TYPE );
        this.livemode = livemode;
        this.amount = amount;
        this.card = card;
        this.created = created;
        this.currency = currency;
        this.fee = fee;
        this.feeDetails = ImmutableList.copyOf( feeDetails );
        this.paid = paid;
        this.refunded = refunded;
        this.amountRefunded = amountRefunded;
        this.customer = customer;
        this.description = description;
        this.dispute = dispute;
        this.failureMessage = failureMessage;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( CREATED_KEY, getCreated() )
                .add( LIVEMODE_KEY, isLivemode() )
                .add( CUSTOMER_KEY, getCustomerId() )
                .add( DESCRIPTION_KEY, getDescription() )
                .add( CARD_KEY, getCard() )
                .add( FEE_KEY, getFee() )
                .add( FEE_DETAILS_KEY, getFeeDetails() )
                .add( AMOUNT_KEY, getAmount() )
                .add( CURRENCY_KEY, getCurrency() )
                .add( PAID_KEY, isPaid() )
                .add( REFUNDED_KEY, isRefunded() )
                .add( AMOUNT_REFUNDED_KEY, getAmountRefunded() )
                .add( DISPUTE_KEY, getDispute() )
                .add( FAILURE_MESSAGE_KEY, getFailureMessage() )
                .toString();
    }

    public boolean isLivemode() {
        return livemode;
    }

    public int getAmount() {
        return amount;
    }

    public Card getCard() {
        return card;
    }

    public long getCreated() {
        return created;
    }

    public String getCurrency() {
        return currency;
    }

    public int getFee() {
        return fee;
    }

    public ImmutableList<FeeDetail> getFeeDetails() {
        return feeDetails;
    }

    public boolean isPaid() {
        return paid;
    }

    public boolean isRefunded() {
        return refunded;
    }

    public int getAmountRefunded() {
        return amountRefunded;
    }

    public Optional<String> getCustomerId() {
        return customer;
    }

    public Optional<String> getDescription() {
        return description;
    }

    public Optional<Dispute> getDispute() {
        return dispute;
    }

    public Optional<String> getFailureMessage() {
        return failureMessage;
    }

    private static final String AMOUNT_KEY = "amount";
    private static final String CARD_KEY = "card";
    private static final String CURRENCY_KEY = "currency";
    private static final String FEE_KEY = "fee";
    private static final String FEE_DETAILS_KEY = "fee_details";
    private static final String PAID_KEY = "paid";
    private static final String REFUNDED_KEY = "refunded";
    private static final String AMOUNT_REFUNDED_KEY = "amount_refunded";
    private static final String CUSTOMER_KEY = "customer";
    private static final String DESCRIPTION_KEY = "description";
    private static final String DISPUTE_KEY = "dispute";
    private static final String FAILURE_MESSAGE_KEY = "failure_message";
    public static final String STRIPE_TYPE = "charge";

    @JsonInclude( Include.NON_NULL )
    @JsonIgnoreProperties( ignoreUnknown = true )
    @JsonAutoDetect( getterVisibility = Visibility.NONE,
            isGetterVisibility = Visibility.NONE,
            creatorVisibility = Visibility.NONE,
            fieldVisibility = Visibility.NONE,
            setterVisibility = Visibility.NONE )
    public class Dispute {

        @JsonProperty( CREATED_KEY ) private final long created;
        @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
        @JsonProperty( REASON_KEY ) private final String reason;
        @JsonProperty( STATUS_KEY ) private final String status;
        @JsonProperty( AMOUNT_KEY ) private final int amount;
        @JsonProperty( CURRENCY_KEY ) private final String currency;
        @JsonProperty( EVIDENCE_KEY ) private final Optional<String> evidence;
        @JsonProperty( EVIDENCE_DUE_BY_KEY ) private final long evidenceDueBy;

        @JsonCreator
        private Dispute( @JsonProperty( CREATED_KEY ) final long created,
                @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
                @JsonProperty( REASON_KEY ) final String reason,
                @JsonProperty( STATUS_KEY ) final String status,
                @JsonProperty( AMOUNT_KEY ) final int amount,
                @JsonProperty( CURRENCY_KEY ) final String currency,
                @JsonProperty( EVIDENCE_KEY ) final Optional<String> evidence,
                @JsonProperty( EVIDENCE_DUE_BY_KEY ) final long evidenceDueBy ) {
            this.livemode = livemode;
            this.amount = amount;
            this.created = created;
            this.currency = currency;
            this.evidenceDueBy = evidenceDueBy;
            this.reason = reason;
            this.status = status;
            this.evidence = evidence;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper( this )
                    .add( CREATED_KEY, getCreated() )
                    .add( LIVEMODE_KEY, isLivemode() )
                    .add( REASON_KEY, getReason() )
                    .add( STATUS_KEY, getStatus() )
                    .add( AMOUNT_KEY, getAmount() )
                    .add( CURRENCY_KEY, getCurrency() )
                    .add( EVIDENCE_KEY, getEvidence() )
                    .add( EVIDENCE_DUE_BY_KEY, getEvidenceDueBy() )
                    .toString();
        }

        public Charge getCharge() {
            return Charge.this;
        }

        public boolean isLivemode() {
            return livemode;
        }

        public int getAmount() {
            return amount;
        }

        public long getCreated() {
            return created;
        }

        public String getCurrency() {
            return currency;
        }

        public long getEvidenceDueBy() {
            return evidenceDueBy;
        }

        public String getReason() {
            return reason;
        }

        public String getStatus() {
            return status;
        }

        public Optional<String> getEvidence() {
            return evidence;
        }

        private static final String EVIDENCE_DUE_BY_KEY = "evidence_due_by";
        private static final String REASON_KEY = "reason";
        private static final String STATUS_KEY = "status";
        private static final String EVIDENCE_KEY = "evidence";

    }

    @JsonInclude( Include.NON_NULL )
    @JsonIgnoreProperties( ignoreUnknown = true )
    @JsonAutoDetect( getterVisibility = Visibility.NONE,
            isGetterVisibility = Visibility.NONE,
            creatorVisibility = Visibility.NONE,
            fieldVisibility = Visibility.NONE,
            setterVisibility = Visibility.NONE )
    public static class FeeDetail {

        @JsonProperty( AMOUNT_KEY ) private final int amount;
        @JsonProperty( CURRENCY_KEY ) private final String currency;
        @JsonProperty( TYPE_KEY ) private final String type;
        @JsonProperty( DESCRIPTION_KEY ) private final Optional<String> description;
        @JsonProperty( APPLICATION_KEY ) private final Optional<String> application;

        @JsonCreator
        private FeeDetail( @JsonProperty( AMOUNT_KEY ) final int amount,
                @JsonProperty( CURRENCY_KEY ) final String currency,
                @JsonProperty( TYPE_KEY ) final String type,
                @JsonProperty( DESCRIPTION_KEY ) final Optional<String> description,
                @JsonProperty( APPLICATION_KEY ) final Optional<String> application ) {
            this.amount = amount;
            this.currency = currency;
            this.type = type;
            this.description = description;
            this.application = application;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper( this )
                    .add( APPLICATION_KEY, getApplication() )
                    .add( TYPE_KEY, getType() )
                    .add( DESCRIPTION_KEY, getDescription() )
                    .add( AMOUNT_KEY, getAmount() )
                    .add( CURRENCY_KEY, getCurrency() )
                    .toString();
        }

        public int getAmount() {
            return amount;
        }

        public String getCurrency() {
            return currency;
        }

        public String getType() {
            return type;
        }

        public Optional<String> getDescription() {
            return description;
        }

        public Optional<String> getApplication() {
            return application;
        }

        private static final String TYPE_KEY = "type";
        private static final String APPLICATION_KEY = "application";

    }

}
