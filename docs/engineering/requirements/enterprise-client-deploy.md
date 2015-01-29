# Enterprise Deployment Requirements

The AeroFS client may be deployed at scale using systems management software. This
document captures changes required to work with such systems for OSX and Windows clients
in large enterprises.

Linux clients are currently out of scope.

iOS and Android deployment in the Enterprise is expected to be provided by the
MDM software catalog systems.


### General Requirements

*REQ*: the auto-update function in the client can be suppressed, in Private Cloud deployment
    only.

    (Note: this may be indicated by a marker file in the application directory.)


### OSX-specific Requirements

*REQ*: OSX installations shall preserve the code-signing seal in the installed application.


*REQ*: AeroFS for OSX can be installed as a root user. Multiple users on the same OSX machine
    should be able to use one Aerofs installation.


*REQ*: The auto-updater is not required to work when the application is owned by the root
    user (or indeed, any other user)[^suppress].

[^suppress]: (Note: so - if installed by the root user, AeroFS auto-update should be suppressed.


### Windows Requirements

*REQ*: An SCCM[^sccm]-compatible version of the installer on Windows can be downloaded from the
    appliance by an Appliance Admin.

[^sccm]: SCCM : Windows System Center Configuration Manager, the current name for Microsoft's systems management software)

*REQ*: The SCCM-compatible installer is modified in the repackaging step to include the
    AeroFS Appliance URL and certificate. This may be as simple as modifying the msi
    database.


*REQ*: The SCCM-compatible installer is not required to be Authenticode-signed.


*REQ*: the SCCM-compatible installer must not install to any user-specific location[^appdata].
    The software management system should provide the destination path - defaulting to
    Program Files.

[^appdata]: SCCM compatibility requires that we allow the installer to run as SYSTEM. N.B. implication: no, you can't install in %AppData%, since it won't exist.


## Future Enhancements

The following are nice to have but not required immediately.

 - AeroFS binaries (aerofsd, dlls, aerofs.exe) should be Authenticode-signed.

 - enterprise admins should be able to supply their own signing certificate to the
 appliance. If that is done, the repackaging step[^cert2] should also sign the Windows installer
 with the provided signing certificate.[^cert]

[^cert]: this is a reasonable option if the site has a CA, and that CA has been added to the Windows trusted-CA's list.

[^cert2]: this requires that we ship a linux signing tool (osslsigncode) and do the UX and Bootstrap work to modify the installer packages on Windows.)


 - clients could ask the appliance for the "minimum version". If the client
 is not configured for auto-update, we can present a helpful user-visible message like:

    "Your AeroFS client is out of date (you have 0.8.62, required is 0.8.70). This should
     be resolved by your network Administrator. Click here to send a request email to your
     admin."

  Frankly, we don't introduce client-compatibility breaking changes very often. But when
  we do... we don't want the user to say "AeroFS is broken", we want them to say "hey,
  local IT guy, *you* forgot to update *my* AeroFS".


## Implementation notes: signing code on OSX

  - the site-config.properties hack on OSX works just fine with code-signing, no change
  required for this platform.

  - Note we really must maintain the signing on Mac, as Mavericks and later may treat
  unsigned software as "corrupted" and refuse to run it.

  I will be happy to confirm the above with Dom at CBX.


## Implementation notes: signing code on Windows

Background - the current situation:

  - the downloaded installer is unsigned; it installs the site-config.properties
    and then runs the signed client installer.

  - keeping the inner (signed) client installer is nice; the UAC popup
    shows that it is "AeroFS" requesting the privilege as opposed to
    "an unknown publisher".


Preferred situation:

  - Enterprise installer should be .msi for better integration with SMS/SCCM and
    Group Policy.

  - options for building this are commercial product (BitRock Installbuilder, few k$)
    or hack something up using GNOME msitool or similar.

  - some msi-editing technology will be required on the appliance. Validate whether we
    can 'repackage' the installer using msitool (either add a file or add a URL and cert
    to the msi database)
