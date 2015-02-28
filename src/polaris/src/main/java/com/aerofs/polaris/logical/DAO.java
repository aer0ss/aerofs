package com.aerofs.polaris.logical;

import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.Locations;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.LogicalTimestamps;
import com.aerofs.polaris.dao.ObjectProperties;
import com.aerofs.polaris.dao.ObjectTypes;
import com.aerofs.polaris.dao.Transforms;
import org.skife.jdbi.v2.Handle;

/**
 * Holder for all DAO classes required to
 * interact with the Polaris logical-object database.
 */
public final class DAO {

    final Children children;
    final Locations locations;
    final LogicalObjects objects;
    final ObjectProperties objectProperties;
    final ObjectTypes objectTypes;
    final Transforms transforms;
    final LogicalTimestamps logicalTimestamps;

    DAO(Handle conn) {
        this.children = conn.attach(Children.class);
        this.locations = conn.attach(Locations.class);
        this.objects = conn.attach(LogicalObjects.class);
        this.objectProperties = conn.attach(ObjectProperties.class);
        this.objectTypes = conn.attach(ObjectTypes.class);
        this.transforms = conn.attach(Transforms.class);
        this.logicalTimestamps = conn.attach(LogicalTimestamps.class);
    }
}
