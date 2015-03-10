package com.aerofs.polaris.acl;

import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;

/**
 * Implemented by classes that can perform ACL checks whenever:
 * <ul>
 *     <li>An entity wants to query the polaris logical-object database.</li>
 *     <li>An entity wants to update (create, update, remove)
 *         objects in the polaris logical-object database.</li>
 * </ul>
 * Implementations <strong>MUST</strong> be thread-safe.
 */
public interface AccessManager {

    /**
     * Check if {@code user} can access {@code store}
     * with the {@code requested} permissions.
     * <br>
     * Note that this is a <strong>BLOCKING</strong> call.
     * Implementations <strong>MUST</strong> return <strong>ONLY</strong>
     * when the access check is complete.
     *
     * @param user user id of the user who wants to access the shared folder
     * @param store oid of the store store or shared folder the user wants to access
     * @param requested one or more permissions the user wants when accessing the store store or shared folder
     * @throws AccessException if <strong>any</strong> of the {@code requested}
     * permissions cannot be granted to the user
     */
    void checkAccess(UserID user, UniqueID store, Access... requested) throws AccessException;
}
