package com.aerofs.trifrost.db;

import com.aerofs.trifrost.api.Device;
import com.google.common.base.Strings;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 */
@RegisterMapper(Devices.Mapper.class)
public interface Devices {

    @SqlUpdate(
            "INSERT INTO devices (dev_id, user_id, dev_name, dev_family, push_type, push_token) " +
                "VALUES (:dev_id, :user_id, :d.name, :d.family, :d.pushType, :d.pushToken)")
    public int add(@Bind("dev_id") String deviceId,
                   @Bind("user_id") String userId,
                   @BindBean("d") Device device);

    @RegisterMapper(Devices.Mapper.class)
    @SqlQuery(
            "SELECT * " +
                    "FROM devices " +
                    "WHERE dev_id = :dev_id and user_id = :user_id")
    Device get(@Bind("user_id") String userId,
                      @Bind("dev_id") String deviceId);

    @SqlUpdate(
            "UPDATE devices " +
                "SET " +
                    "dev_name = :d.name, " +
                    "dev_family = :d.family, " +
                    "push_type = :d.pushType, " +
                    "push_token = :d.pushToken " +
                "WHERE " +
                    "user_id = :user_id and dev_id = :dev_id")
    int update(
            @Bind("user_id") String userId,
            @Bind("dev_id") String deviceId,
            @BindBean("d") Device device);

    final class Mapper implements ResultSetMapper<Device> {
        @Override
        public Device map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            String name = r.getString("dev_name");
            String family = r.getString("dev_family");
            Device.PushType pushType = (Strings.isNullOrEmpty(r.getString("push_type"))) ? null : Device.PushType.valueOf(r.getString("push_type").toUpperCase());
            String pushToken = r.getString("push_token");
            return new Device(name, family, pushType, pushToken);
        }
    }
}
