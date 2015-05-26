    README
    ======

Scripts and artifacts needed to build an Enterprise installer for Windows.

We build a one-file installer with a special extra site-config by appending
three parts together in a very unsophisticated way. This is fragile (yay Windows)
so please proceed with caution.


The three parts that we smash together are:

 - an SFX installer preamble. This is a self-contained PE32 executable that
 reaches inside itself (at an offset) to find a config file and an embedded archive.
 We are using the 7zip SFX installer from 7-zip.org, with the resources adjusted
 slightly. See below for details.

 - append a configuration file. The format of this file is very fussy, and it must
 be UTF8. Modify it carefully; or even better, not at all. Also note that the
 SFX installer does not do what its doc say it does. Fun!

 - append a 7-zip archive that has all the resources referred to by the config file.
 In our case, those resources are:
    - an INF file that installs site-config.properties (AeroEnterprise.inf)
    - Site properties (site-config.properties)
    - the regular client installer (AeroFSInstall.exe)

The installer will extract the contents to a temp dir, do whatever is specified
by the installer configuration, and then clean up the temp dir.

Arguments that are not parsed by the SFX preamble are passed to the RunProgram value
in the configuration file. Meaning,
   AeroEnterprise.exe /S
will engage silent mode on the AeroFSInstall.exe installer.



    UPDATING THE SELF-EXTRACTOR
Or,
    ABANDON ALL HOPE YE WHO ENTER HERE
    ==================================

Start with the 7-zip libraries and SFX archive:
    http://downloads.sourceforge.net/sevenzip/7z920_extra.7z

Find the Windows SFX Installer: 7zS.sfx

Using the delightful resource editor "reshack" on a Windows machine,
start by replacing the icons (if you like). Adjust the Executable properties in the
Version Info builtin resource section.

Now we have to add a manifest to disable the implicit UAC for the outer shell,
otherwise the updater will break.

The resource to be added should use the following values:
    Resource Type: 24
    Resource Name: 1
    Language: 1033
    Contents:
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
<assemblyIdentity version="1.0.0.0" processorArchitecture="X86" name="AeroEnterprise" type="win32"></assemblyIdentity>
<trustInfo xmlns="urn:schemas-microsoft-com:asm.v3">
<security>
<requestedPrivileges>
<requestedExecutionLevel level="asInvoker"></requestedExecutionLevel>
</requestedPrivileges>
</security>
</trustInfo>
</assembly>


...and you're done! Magic.

jP 2013/06/25

    Usage Example
    =============

./build_installer.sh -f AeroEnterprise.inf -f somepath/site-config.properties -f somepath/AeroFSInstall.exe -o AeroEnterprise.exe
