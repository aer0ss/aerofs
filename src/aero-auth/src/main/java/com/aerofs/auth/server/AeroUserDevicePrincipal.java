package com.aerofs.auth.server;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;

/**
 * Identifies an AeroFS end-user/device pair.
 * <br>
 * This can represent:
 * <ul>
 *     <li>An AeroFS desktop client.</li>
 *     <li>An AeroFS mobile device.</li>
 *     <li>A web session initiated by an AeroFS user.</li>
 * </ul>
 */
public interface AeroUserDevicePrincipal extends AeroPrincipal {

    /**
     * Get the unique user id of the user that made the request
     *
     * @return unique user id of the user that made the request
     */
    UserID getUser();

    /**
     * Return the device id of the device that made the request
     *
     * @return device id of the device that made the request
     */
    DID getDevice();
}
