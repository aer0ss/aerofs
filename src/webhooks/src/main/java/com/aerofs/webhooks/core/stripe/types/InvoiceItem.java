package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

@JsonInclude( Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonAutoDetect( getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE )
public class InvoiceItem extends AbstractIdentifiedStripeApiObject {

    @JsonProperty( LIVEMODE_KEY ) private final boolean livemode;
    @JsonProperty( AMOUNT_KEY ) private final int amount;
    @JsonProperty( CURRENCY_KEY ) private final String currency;
    @JsonProperty( PERIOD_KEY ) private final Period period;
    @JsonProperty( PRORATION_KEY ) private final boolean proration;
    @JsonProperty( ITEM_TYPE_KEY ) private final String itemType;
    @JsonProperty( DESCRIPTION_KEY ) private final Optional<String> description;
    @JsonProperty( PLAN_KEY ) private final Optional<Plan> plan;
    @JsonProperty( QUANTITY_KEY ) private final Optional<Integer> quantity;

    @JsonCreator
    protected InvoiceItem( @JsonProperty( ID_KEY ) final String id,
            @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( LIVEMODE_KEY ) final boolean livemode,
            @JsonProperty( AMOUNT_KEY ) final int amount,
            @JsonProperty( CURRENCY_KEY ) final String currency,
            @JsonProperty( PERIOD_KEY ) final Period period,
            @JsonProperty( PRORATION_KEY ) final boolean proration,
            @JsonProperty( ITEM_TYPE_KEY ) final String itemType,
            @JsonProperty( DESCRIPTION_KEY ) final Optional<String> description,
            @JsonProperty( PLAN_KEY ) final Optional<Plan> plan,
            @JsonProperty( QUANTITY_KEY ) final Optional<Integer> quantity ) {
        super( id, STRIPE_TYPE );
        this.livemode = livemode;
        this.amount = amount;
        this.currency = currency;
        this.period = period;
        this.proration = proration;
        this.itemType = itemType;
        this.description = description;
        this.plan = plan;
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( ID_KEY, getId() )
                .add( ITEM_TYPE_KEY, getItemType() )
                .add( DESCRIPTION_KEY, getDescription() )
                .add( AMOUNT_KEY, getAmount() )
                .add( CURRENCY_KEY, getCurrency() )
                .add( PERIOD_KEY, getPeriod() )
                .add( PRORATION_KEY, isProration() )
                .add( PLAN_KEY, getPlan() )
                .add( QUANTITY_KEY, getQuantity() )
                .toString();
    }

    public boolean isLivemode() {
        return livemode;
    }

    public int getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Period getPeriod() {
        return period;
    }

    public boolean isProration() {
        return proration;
    }

    public String getItemType() {
        return itemType;
    }

    public Optional<String> getDescription() {
        return description;
    }

    public Optional<Plan> getPlan() {
        return plan;
    }

    public Optional<Integer> getQuantity() {
        return quantity;
    }

    protected static final String AMOUNT_KEY = "amount";
    protected static final String CURRENCY_KEY = "currency";
    protected static final String PERIOD_KEY = "period";
    protected static final String PRORATION_KEY = "proration";
    protected static final String DESCRIPTION_KEY = "description";
    protected static final String ITEM_TYPE_KEY = "type";
    protected static final String PLAN_KEY = "plan";
    protected static final String QUANTITY_KEY = "quantity";
    public static final String STRIPE_TYPE = "invoiceitem";

    @JsonInclude( Include.NON_NULL )
    @JsonIgnoreProperties( ignoreUnknown = true )
    @JsonAutoDetect( getterVisibility = Visibility.NONE,
            isGetterVisibility = Visibility.NONE,
            creatorVisibility = Visibility.NONE,
            fieldVisibility = Visibility.NONE,
            setterVisibility = Visibility.NONE )
    public static class Period {

        @JsonProperty( START_KEY ) private final long start;
        @JsonProperty( END_KEY ) private final long end;

        @JsonCreator
        private Period( @JsonProperty( START_KEY ) final long start,
                @JsonProperty( END_KEY ) final long end ) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper( this )
                    .add( START_KEY, getStart() )
                    .add( END_KEY, getEnd() )
                    .toString();
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        private static final String START_KEY = "start";
        private static final String END_KEY = "end";

    }

}
