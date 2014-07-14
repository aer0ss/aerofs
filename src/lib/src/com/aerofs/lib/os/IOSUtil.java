package com.aerofs.lib.os;

import java.io.IOException;

import com.aerofs.lib.OutArg;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil.Icon;
import com.aerofs.lib.os.OSUtil.OSFamily;

import javax.annotation.Nullable;

public interface IOSUtil
{
    OSFamily getOSFamily();

    /**
     * @return Full OS name, including the version name
     * Examples:
     *  - on windows: "Windows XP", "Windows 2003", "Windows 7", etc...
     *  - on osx: "Mac OS X 10.6.8", "Mac OS X 10.8.2", etc...
     *  - on linux: there is no standard way to get the distro name, so we try to read /etc/*release.
     *  If that fails, we will simply return "Linux", otherwise "Ubuntu 11.10", etc..
     */
    String getFullOSName();

    String getDefaultRTRoot();

    // we don't use SystemUtil.loadLibrary() directly as it doesn't work well on Windows
    void loadLibrary(String library);

    /**
     * N.B. never call this method directly. call Setup.getDefaultAnchorRoot() instead.
     */
    String getDefaultRootAnchorParent();

    void addToFavorite(String path) throws IOException;

    void removeFromFavorite(String path) throws IOException;

    /**
     * N.B. this method may be expensive. use sparingly
     *
     * @param remote set to null if unknown
     * @return capitalized file system type
     */
    String getFileSystemType(String path, OutArg<Boolean> remote)
        throws IOException;

    /**
     * @return true if paths p1 and p2 reside on the same filesystem.
     *
     * N.B. this method may be expensive. use sparingly
     *
     * TODO remove this method after we can tell the mount point of a given path
     * on Windows, so we can compare mount points instead
     */
    boolean isInSameFileSystem(String p1, String p2) throws IOException;

    /**
     * @param remote null if unknown, as set by getFileSystemType
     */
    boolean isFileSystemTypeSupported(String type, Boolean remote);

    /**
     * @return the user-friendly, platform-dependant name that should be used in dialogs.
     * e.g.: "Finder Extension" on OS X, etc...
     */
    String getShellExtensionName();

    /**
     * @return true if there is a shell extension available for this platform
     */
    boolean isShellExtensionAvailable();

    boolean isShellExtensionInstalled();

    /**
     * Mark the file specified in the path as a hidden, system file. Nop on non-Windows OSes
     */
    void markHiddenSystemFile(String absPath) throws IOException;

    /**
     * Installs the Shell Extension
     * @param silently true is this method should not prompt the user for admin privileges or
     * anything.
     * @throws IOException
     * @throws SecurityException
     */
    void installShellExtension(boolean silently) throws IOException, SecurityException;

    /**
     * Called once the GUI protobuf server is up and running, to tell the shell
     * extension to connect.
     * @param port The port number that the shell extension should connect to
     */
    void startShellExtension(int port);

    /**
     *  @param exclusive whether to avoid overwriting preexisting files
     *  @throws IOException if there are preexisting files with exclusive flag turned on
     */
    void copyRecursively(InjectableFile source, InjectableFile destination, boolean exclusive,
            boolean keepMTime) throws IOException;

    /**
     * Launches the OS file manager showing a specific path (file or folder)
     * This action is commonly implemented as "Show in folder" in the UI
     */
    void showInFolder(String path);

    /**
     * @return a "clean" name guarenteed to be representable on this OS
     */
    String cleanFileName(String name);

    /**
     * Use when accepting a local filename as input (Notifier, Shellext, ...)
     * See implementation in {@link OSUtilOSX} for the gory details
     */
    String normalizeInputFilename(String name);

    /**
     * whether a filename is invalid (i.e. inherently non-representable) on this OS
     */
    boolean isInvalidFileName(String name);

    /**
     * @return a user-friendly explanation of the reason why a filename is invalid,
     * null if the name is valid.
     */
    @Nullable String reasonForInvalidFilename(String name);

    /**
     * Return the path to an OS-specific icon resource
     * We need this method because those icons are not necessarily stored under approot like other
     * image resources. On Windows, they are at the top-level folder so that their path stays
     * constant across versions.
     */
    String getIconPath(Icon icon);
}
