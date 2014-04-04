package com.aerofs.webhooks;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.client.JerseyClientConfiguration;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.db.DatabaseConfiguration;

public class WebhooksConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty
    private JerseyClientConfiguration customerioHttpClient;
    
    @Valid
    @NotNull
    @JsonProperty
    private DatabaseConfiguration database = new DatabaseConfiguration();

    public JerseyClientConfiguration getCustomerioHttpClientConfiguration() {
        return customerioHttpClient;
    }

    public DatabaseConfiguration getDatabaseConfiguration() {
        return database;
    }

}
