Synopsis
---
We build and ship a MSI installer (.msi) for Windows to meet the [enterprise
deployment requirements](../requirements/enterprise-client-deploy.md).

Technologies Used
---
We use the following technologies to build MSI installers.

- [Windows Installer](https://msdn.microsoft.com/en-us/library/cc185688%28v=vs.85%29.aspx)
- [WiX Toolset](http://wixtoolset.org)
- [msitools](https://wiki.gnome.org/msitools)

A MSI installer is simply a database containing a set of tables containing
instructions and a list of binary streams containing either file data or
executables.

**Windows Installer** is the underlying installation technology. It reads and
interprets the data in the MSI (according to a schema defined by Windows
Installer) and performs the actual installation tasks.

**WiX Toolset** is used to build MSI installers on Windows. While the support
and resources for WiX is extensive, we chose not to build on Windows because
reasons.

**msitools** is a set of utilities to manipulate MSI installer files on
Linux/Unix systems.

Target
---
The desired output of the entire build process is a single MSI installer (one
for client, one for TeamServer) that does the following:

- Support install, uninstall, repair, and upgrade.
- Install all files to Program Files or wherever the admin chooses.
- Integrate with Windows Add/Remove Programs, the registry of all installed
  programs.
- Register shell extension and integrate with the Windows shell.
- Add firewall exceptions for AeroFS.
- Create appropriate shortcuts in the programs menu and start up.
- Automatically restart explorer to cleanly install/uninstall the shell
  extension.
- Unregister shell extensions and firewall exceptions when AeroFS is
  uninstalled.

Build Process
---
The complete build process is divided into these steps:

- Build base installers or shims on Windows during development.
  * the shims will have a small set of stub files and instructions on what to
    do in addition to installing the file (because an installation isn't just
    copying the files).
  * the output artifact will be checked-in into the repo.
- Build a complete installer on OSX during deploy.
  * this is done using msitools provided by Homebrew.
  * this adds an additional step during deploy where we take the base installer
    from Windows and inject all the files into it.
  * this produces a complete MSI installer which can be used on its own.
- Inject site-config into the installer on Ubuntu during repackaging.
  * this is done using msitools compiled from source.
  * this produces the final MSI installer for enterprise deployments.

References
---
This section should reference all code related to building MSI installers

To build MSI installers on Windows, check out `msi-installers` project from
Gerrit. This project contains resource to build the shim we keep in the repo as
well as a full-fledge installer. See README in the project for details.

For reference on populating the shim to full installer on OS X, see
`repos/aerofs/tools/build/make_client_installers`.

For reference on compiling msitools to run on Linux, see
`repos/packaging/repackaging/root/opt/msitools/install_msitools`.

For reference on injecting site-config during repackaging, see
`repos/packaging/repackaging`.
