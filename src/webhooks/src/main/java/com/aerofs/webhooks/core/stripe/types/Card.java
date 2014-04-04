package com.aerofs.webhooks.core.stripe.types;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
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
public class Card extends AbstractStripeApiObject {

    @JsonProperty( EXP_MONTH_KEY ) private final int expMonth;
    @JsonProperty( EXP_YEAR_KEY ) private final int expYear;
    @JsonProperty( FINGERPRINT_KEY ) private final String fingerprint;
    @JsonProperty( LAST4_KEY ) private final String last4;
    @JsonProperty( CARD_TYPE_KEY ) private final String cardType;
    @JsonProperty( ADDRESS_CITY_KEY ) private final Optional<String> addressCity;
    @JsonProperty( ADDRESS_COUNTRY_KEY ) private final Optional<String> addressCountry;
    @JsonProperty( ADDRESS_LINE1_KEY ) private final Optional<String> addressLine1;
    @JsonProperty( ADDRESS_LINE1_CHECK_KEY ) private final Optional<String> addressLine1Check;
    @JsonProperty( ADDRESS_LINE2_KEY ) private final Optional<String> addressLine2;
    @JsonProperty( ADDRESS_STATE_KEY ) private final Optional<String> addressState;
    @JsonProperty( ADDRESS_ZIP_KEY ) private final Optional<String> addressZip;
    @JsonProperty( ADDRESS_ZIP_CHECK_KEY ) private final Optional<String> addressZipCheck;
    @JsonProperty( COUNTRY_KEY ) private final Optional<String> country;
    @JsonProperty( CVCCHECK_KEY ) private final Optional<String> cvcCheck;
    @JsonProperty( NAME_KEY ) private final Optional<String> name;
    @JsonProperty( TYPE_KEY ) final String type = STRIPE_TYPE;

    @JsonCreator
    private Card( @JsonProperty( TYPE_KEY ) final String type,
            @JsonProperty( EXP_MONTH_KEY ) final int expMonth,
            @JsonProperty( EXP_YEAR_KEY ) final int expYear,
            @JsonProperty( FINGERPRINT_KEY ) final String fingerprint,
            @JsonProperty( LAST4_KEY ) final String last4,
            @JsonProperty( CARD_TYPE_KEY ) final String cardType,
            @JsonProperty( ADDRESS_CITY_KEY ) final Optional<String> addressCity,
            @JsonProperty( ADDRESS_COUNTRY_KEY ) final Optional<String> addressCountry,
            @JsonProperty( ADDRESS_LINE1_KEY ) final Optional<String> addressLine1,
            @JsonProperty( ADDRESS_LINE1_CHECK_KEY ) final Optional<String> addressLine1Check,
            @JsonProperty( ADDRESS_LINE2_KEY ) final Optional<String> addressLine2,
            @JsonProperty( ADDRESS_STATE_KEY ) final Optional<String> addressState,
            @JsonProperty( ADDRESS_ZIP_KEY ) final Optional<String> addressZip,
            @JsonProperty( ADDRESS_ZIP_CHECK_KEY ) final Optional<String> addressZipCheck,
            @JsonProperty( COUNTRY_KEY ) final Optional<String> country,
            @JsonProperty( CVCCHECK_KEY ) final Optional<String> cvcCheck,
            @JsonProperty( NAME_KEY ) final Optional<String> name ) {
        super( STRIPE_TYPE );

        this.expMonth = expMonth;
        this.expYear = expYear;
        this.fingerprint = fingerprint;
        this.last4 = last4;
        this.cardType = cardType;
        this.addressCity = addressCity;
        this.addressCountry = addressCountry;
        this.addressLine1 = addressLine1;
        this.addressLine1Check = addressLine1Check;
        this.addressLine2 = addressLine2;
        this.addressState = addressState;
        this.addressZip = addressZip;
        this.addressZipCheck = addressZipCheck;
        this.country = country;
        this.cvcCheck = cvcCheck;
        this.name = name;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper( this )
                .omitNullValues()
                .add( FINGERPRINT_KEY, getFingerprint() )
                .add( NAME_KEY, getName() )
                .add( COUNTRY_KEY, getCountry() )
                .add( CARD_TYPE_KEY, getCardType() )
                .add( LAST4_KEY, getLast4() )
                .add( EXP_MONTH_KEY, getExpMonth() )
                .add( EXP_YEAR_KEY, getExpYear() )
                .add( CVCCHECK_KEY, getCvcCheck() )
                .add( ADDRESS_LINE1_KEY, getAddressLine1() )
                .add( ADDRESS_LINE2_KEY, getAddressLine2() )
                .add( ADDRESS_STATE_KEY, getAddressState() )
                .add( ADDRESS_COUNTRY_KEY, getAddressCountry() )
                .add( ADDRESS_ZIP_KEY, getAddressZip() )
                .add( ADDRESS_LINE1_CHECK_KEY, getAddressLine1Check() )
                .add( ADDRESS_ZIP_CHECK_KEY, getAddressZipCheck() )
                .toString();
    }

    public int getExpMonth() {
        return expMonth;
    }

    public int getExpYear() {
        return expYear;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getLast4() {
        return last4;
    }

    public String getCardType() {
        return cardType;
    }

    public Optional<String> getAddressCity() {
        return addressCity;
    }

    public Optional<String> getAddressCountry() {
        return addressCountry;
    }

    public Optional<String> getAddressLine1() {
        return addressLine1;
    }

    public Optional<String> getAddressLine1Check() {
        return addressLine1Check;
    }

    public Optional<String> getAddressLine2() {
        return addressLine2;
    }

    public Optional<String> getAddressState() {
        return addressState;
    }

    public Optional<String> getAddressZip() {
        return addressZip;
    }

    public Optional<String> getAddressZipCheck() {
        return addressZipCheck;
    }

    public Optional<String> getCountry() {
        return country;
    }

    public Optional<String> getCvcCheck() {
        return cvcCheck;
    }

    public Optional<String> getName() {
        return name;
    }

    private static final String ADDRESS_ZIP_CHECK_KEY = "address_zip_check";
    private static final String NAME_KEY = "name";
    private static final String CVCCHECK_KEY = "cvc_check";
    private static final String COUNTRY_KEY = "country";
    private static final String ADDRESS_ZIP_KEY = "address_zip";
    private static final String ADDRESS_STATE_KEY = "address_state";
    private static final String ADDRESS_LINE1_CHECK_KEY = "address_line1_check";
    private static final String ADDRESS_LINE1_KEY = "address_line1";
    private static final String ADDRESS_CITY_KEY = "address_city";
    private static final String ADDRESS_COUNTRY_KEY = "address_country";
    private static final String CARD_TYPE_KEY = "type";
    private static final String LAST4_KEY = "last4";
    private static final String FINGERPRINT_KEY = "fingerprint";
    private static final String EXP_YEAR_KEY = "exp_year";
    private static final String EXP_MONTH_KEY = "exp_month";
    private static final String ADDRESS_LINE2_KEY = "address_line2";
    public static final String STRIPE_TYPE = "card";

}
