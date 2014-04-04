package com.aerofs.webhooks.core.stripe;

import com.aerofs.customerio.CustomerioClient;
import com.aerofs.webhooks.entities.OrganizationDao;
import com.aerofs.webhooks.entities.UserDao;
import com.google.inject.Inject;

public class ProcessContext {

    private final CustomerioClient customerioClient;
    private final OrganizationDao organizationDao;
    private final UserDao userDao;

    @Inject
    public ProcessContext( final OrganizationDao organizationDao, final UserDao userDao,
            final CustomerioClient customerioClient ) {
        this.organizationDao = organizationDao;
        this.customerioClient = customerioClient;
        this.userDao = userDao;
    }

    public CustomerioClient getCustomerioClient() {
        return customerioClient;
    }

    public OrganizationDao getOrganizationDao() {
        return organizationDao;
    }

    public UserDao getUserDao() {
        return userDao;
    }

}
