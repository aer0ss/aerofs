package com.aerofs.auth.server;

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
    String getUser();

    /**
     * Return the DID (32 character hex string) of the device that made the request
     *
     * @return DID of the device that made the request
     */
    String getDevice();
}
