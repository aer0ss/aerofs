package com.aerofs.polaris.dao;

import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface Locations {

    @SqlQuery("select did from locations where oid = :oid and version = :version")
    ResultIterator<String> get(@Bind("oid") String oid, @Bind("version") long version);

    @SqlUpdate("insert into locations(oid, version, did) values(:oid, :version, :did)")
    void add(@Bind("oid") String oid, @Bind("version") long version, @Bind("did") String did);

    @SqlUpdate("delete from locations where oid = :oid and version = :version and did = :did")
    void remove(@Bind("oid") String oid, @Bind("version") long version, @Bind("did") String did);

    @SuppressWarnings("unused")
    void close();
}