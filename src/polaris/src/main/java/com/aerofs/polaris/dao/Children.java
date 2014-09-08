package com.aerofs.polaris.dao;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface Children {

    @SqlUpdate("insert into children(oid, child_oid, child_name) values(:oid, :child_oid, :child_name)")
    int add(@Bind("oid") String oid, @Bind("child_oid") String child, @Bind("child_name") String childName);

    @SqlUpdate("delete from children where oid = :oid and child_oid = :child_oid")
    int remove(@Bind("oid") String oid, @Bind("child_oid") String child);

    @SqlQuery("select count(child_oid) from children where oid = :oid and child_name = :child_name")
    int countChildrenWithName(@Bind("oid") String oid, @Bind("child_name") String childName);

    @SuppressWarnings("unused")
    void close();
}
