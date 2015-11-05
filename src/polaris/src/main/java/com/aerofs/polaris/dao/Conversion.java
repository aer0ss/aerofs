package com.aerofs.polaris.dao;

import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface Conversion {
    int COMPONENT_META = 1;
    int COMPONENT_CONTENT = 2;

    // N.B. jdbi has some trouble with returning a map, only supports returning iterables for multi-row
    @Mapper(VersionMapper.class)
    @SqlQuery("select did, tick from converted_ticks where oid = :oid and component = :component")
    List<Tick> getDistributedVersion(@Bind("oid") UniqueID oid, @Bind("component") int component);

    @SqlUpdate("delete from converted_ticks where oid = :oid and component = :component")
    int deleteTicks(@Bind("oid") UniqueID oid, @Bind("component") int component);

    @SqlBatch("insert into converted_ticks (oid, component, did, tick) values(:oid, :component, :did, :tick)")
    void insertTick(@Bind("oid") UniqueID oid, @Bind("component") int component, @Bind("did") List<DID> device, @Bind("tick") List<Long> tick);

    @SqlQuery("select target from aliases where alias = :alias and store = :store")
    @Nullable UniqueID getAliasNullable(@Bind("alias") UniqueID alias, @Bind("store") UniqueID store);

    @SqlUpdate("insert into aliases (alias, store, target) values(:alias, :store, :target) on duplicate key update alias=alias")
    int addAlias(@Bind("alias") UniqueID alias, @Bind("store") UniqueID store, @Bind("target") UniqueID target);

    @SqlUpdate("update aliases set target = :newtarget where store = :store and target = :oldtarget")
    int remapAlias(@Bind("store") UniqueID store, @Bind("oldtarget") UniqueID oldTarget, @Bind("newtarget") UniqueID newTarget);

    final class VersionMapper implements ResultSetMapper<Tick>
    {
        private static int DID_COLUMN = 1;
        private static int TICK_COLUMN = 2;

        @Override
        public Tick map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new Tick(DID.fromInternal(r.getBytes(DID_COLUMN)), r.getLong(TICK_COLUMN));
        }
    }

    final class Tick
    {
        public final DID did;
        public final Long l;

        public Tick(DID did, Long l) {
            this.did = did;
            this.l = l;
        }
    }
}

