package com.aerofs.polaris.logical;

import com.aerofs.polaris.dao.*;
import org.skife.jdbi.v2.Handle;

/**
 * Holder for all DAO classes required to
 * interact with the Polaris logical-object database.
 */
public final class DAO {

    public final Children children;
    public final Locations locations;
    public final LogicalObjects objects;
    public final ObjectProperties objectProperties;
    public final ObjectTypes objectTypes;
    public final Transforms transforms;
    final LogicalTimestamps logicalTimestamps;
    final Migrations migrations;
    public final MountPoints mountPoints;

    public DAO(Handle conn) {
        this.children = conn.attach(Children.class);
        this.locations = conn.attach(Locations.class);
        this.objects = conn.attach(LogicalObjects.class);
        this.objectProperties = conn.attach(ObjectProperties.class);
        this.objectTypes = conn.attach(ObjectTypes.class);
        this.transforms = conn.attach(Transforms.class);
        this.logicalTimestamps = conn.attach(LogicalTimestamps.class);
        this.migrations = conn.attach(Migrations.class);
        this.mountPoints = conn.attach(MountPoints.class);
    }
}
