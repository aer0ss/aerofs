package com.aerofs.ca.server.resources;

import com.aerofs.baseline.admin.HealthCheck;
import com.aerofs.baseline.admin.Status;
import com.aerofs.ca.database.CADatabase;
import org.skife.jdbi.v2.DBI;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Context;

@Singleton
@ThreadSafe
public class CAInitializedCheck implements HealthCheck {
    private final CADatabase _db;

    @Inject
    public CAInitializedCheck(@Context DBI dbi)
    {
        this._db = new CADatabase(dbi);
    }

    @Override
    public Status check() {
        if (_db.initialized()) {
            return Status.success();
        } else {
            return Status.failure();
        }

    }
}
