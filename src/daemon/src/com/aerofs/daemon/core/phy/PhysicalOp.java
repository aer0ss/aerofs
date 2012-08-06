package com.aerofs.daemon.core.phy;

/**
 * This enum type specifies appropriate operations on physical objects.
 */
public enum PhysicalOp
{
    /**
     * Perform requested operations on the physical object (e.g. creation, moving, deletion), and
     * establish one-to-one mapping between the logical and physical objects.
     *
     * For linked storage, it's the mapping between SOKIDs and file paths. To maintain the mapping
     * when users change physical file paths, we use FIDs (i.e. i-node numbers) as a bridge.
     *
     * For S3 storage, this mapping is between SOKIDs and S3 data entities. Because data entities
     * are addresses by SOKIDs, the mapping is implicit and no maintenance is required for most
     * cases. The only exception is when the SOKID corresponding to an S3 data entity has
     * changed. And it is possible only in IPhyscialObject#move_ where the source and target
     * physical objects have different SOKIDs. When it happens, the implementation should update the
     * mapping.
     */
    APPLY,

    /**
     * No actual operation is needed on the physical object. Only establish the mapping between the
     * logical and physical object. If the physical object has been mapped to another logical
     * object, the implementation should break the existing mapping before establishing the new one.
     */
    MAP,

    /**
     * The implementation should do nothing. This is needed due to peculiarity of upper level logic.
     * Search for the use of this value for examples.
     */
    NOP
}
