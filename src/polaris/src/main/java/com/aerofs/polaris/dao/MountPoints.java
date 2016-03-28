package com.aerofs.polaris.dao;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.dao.types.OneColumnUniqueIDMapper;

import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import javax.annotation.Nullable;

public interface MountPoints {
    @SqlUpdate("insert into user_mount_points(user_root, mount_point, mount_parent) values (:user_root, :mount_point, :mount_parent)")
    void add(@Bind("user_root") UniqueID rootStore, @Bind("mount_point") UniqueID mountPoint, @Bind("mount_parent") UniqueID mountPointParent);

    @SqlUpdate("delete from user_mount_points where user_root = :user_root and mount_point = :mount_point")
    void remove(@Bind("user_root") UniqueID rootStore, @Bind("mount_point") UniqueID mountPoint);

    @RegisterMapper(OneColumnUniqueIDMapper.class)
    @SqlQuery("select mount_parent from user_mount_points where user_root = :user_root")
    ResultIterator<UniqueID> listUserMountPointParents(@Bind("user_root") UniqueID rootStore);

    @RegisterMapper(OneColumnUniqueIDMapper.class)
    @SqlQuery("select mount_parent from user_mount_points where mount_point = :mount_point")
    ResultIterator<UniqueID> listMountPointParents(@Bind("mount_point") UniqueID mountPoint);

    @Nullable
    @RegisterMapper(OneColumnUniqueIDMapper.class)
    @SqlQuery("select mount_parent from user_mount_points where user_root = :user_root and mount_point = :mount_point")
    UniqueID getMountPointParent(@Bind("user_root") UniqueID rootStore, @Bind("mount_point") UniqueID mountPoint);
}
