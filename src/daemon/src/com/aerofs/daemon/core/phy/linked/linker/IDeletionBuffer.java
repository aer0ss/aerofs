package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.lib.id.SOID;

/**
 * A buffer to store SOIDs that should eventually be deleted from the system.
 */
public interface IDeletionBuffer
{
    /**
     * Add the SOID to be eventually deleted. In case of exceptions, the caller must undo the
     * addition by calling {@link remove_}, otherwise an object may be incorrectly deleted. After
     * the exception, the caller is responsible to retry and re-delete the object.
     *
     * N.B. add the object only if {@link MightDelete#shouldNotDelete(com.aerofs.daemon.core.ds.OA)}
     * returns false.
     */
    void add_(SOID soid);

    /**
     * Remove the SOID from the buffer, so that it will not be deleted.
     */
    void remove_(SOID soid);
}
