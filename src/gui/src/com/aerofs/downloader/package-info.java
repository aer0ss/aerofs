/**
 * This package contains code for the Linux installer, and is deployed as a stand-alone jar file
 * (by build/deployment scripts). Therefore, this package must not depend on any external classes
 * other than the Java API. For the same reason, external classes must not depend on this package.
 *
 * This package is included in the main aerofs jar because at one point in time we screwed up
 * detecting userspace's 32/64-bitness, and needed to download a new package reliably from the
 * context of the updater shell script.  Without knowing the path of the installer, and without
 * making platform assumptions, this was impossible, so we added the downloader to the main jar,
 * added some checking logic to the updater script, and added additional checking logic to the
 * aerofs launcher script.
 *
 * Since that time, all installer scripts have included this check, so it is not safe to remove
 * this package from the main AeroFS jar unless we deploy repositories and remove support for all
 * previous installations.
 */
package com.aerofs.downloader;
