package com.aerofs.polaris.resources;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.dao.Transforms;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

public final class ChangesResource {

    private final DBI dbi;
    private final String oid;

    public ChangesResource(@Context DBI dbi, @PathParam("oid") String oid) {
        this.dbi = dbi;
        this.oid = oid;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Transform> getChangesSince(@QueryParam("since") final long since, @QueryParam("batchSize") final int batchSize) throws InvalidIDException {
        Preconditions.checkArgument(since >= 0, "since parameter %s out of range", since);
        Preconditions.checkArgument(batchSize >= 0, "batchSize parameter %s out of range", batchSize);
        Preconditions.checkArgument(batchSize <= Constants.MAX_RETURNED_TRANSFORMS, "batchSize parameter %s out of range", batchSize);
        Preconditions.checkArgument(Constants.isSharedFolder(oid), "oid %s not an SID", oid);

        return dbi.inTransaction(new TransactionCallback<List<Transform>>() {
            @Override
            public List<Transform> inTransaction(Handle conn, TransactionStatus status) throws Exception {
                List<Transform> returned = Lists.newArrayList();

                Transforms transforms = conn.attach(Transforms.class);
                ResultIterator<Transform> iterator = transforms.getTransformsSince(since, oid);
                try {
                    while (iterator.hasNext() && returned.size() < batchSize) {
                        returned.add(iterator.next());
                    }
                } finally {
                    iterator.close();
                }

                return returned;
            }
        });
    }
}
