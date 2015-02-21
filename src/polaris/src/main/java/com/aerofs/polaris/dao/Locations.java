package com.aerofs.polaris.dao;

import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.dao.types.OneColumnDIDMapper;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

@RegisterMapper(OneColumnDIDMapper.class)
public interface Locations {

    @SqlQuery("select did from locations where oid = :oid and version = :version")
    ResultIterator<DID> get(@Bind("oid") UniqueID oid, @Bind("version") long version);

    @SqlUpdate("insert into locations(oid, version, did) values(:oid, :version, :did)")
    void add(@Bind("oid") UniqueID oid, @Bind("version") long version, @Bind("did") DID device);

    @SqlUpdate("delete from locations where oid = :oid and version = :version and did = :did")
    void remove(@Bind("oid") UniqueID oid, @Bind("version") long version, @Bind("did") DID device);

    @SuppressWarnings("unused")
    void close();

}
