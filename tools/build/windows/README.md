Build box for native client code:
  * Windows 7

# Rebuilding

To re-create this VM, [download]
(https://dev.windows.com/en-us/microsoft-edge/tools/vms/windows/) a base VM
from Microsoft. I used the "Windows 7 with IE8" box on the "Mac OSX" tab. Make
sure you download a VirtualBox-compatible image. This VM will be zipped, unzip
it with 7zip (`brew install p7zip && 7za x file.zip`), since it has been
specifically zipped in such a way as to not be compatible with `unzip`.

Import the `.ova` file you just extracted into VirtualBox. Start the new
machine and deal with the annoying Windows configuration popups.

Open the start menu, find the "Edit group policy" option, and ensure `Computer
Configuration > Windows Settings > Security Settings > Account Policies >
Password Policy`.`Password must meet complexity requirements` is disabled (this
setting varies depending on base Windows version and moon-phase).

Open "Windows Powershell" *as administrator* (right-click -> "Run as
administrator") and execute the following:

    $admin=[adsi]"WinNT://./Administrator,user"
    $admin.psbase.rename("vagrant")
    $admin.SetPassword("vagrant")
    $admin.UserFlags.value = $admin.UserFlags.value -bor 0x10000
    $admin.CommitChanges()

Shut down and restart the VM, since modifying the admin account can f*ck sh*t
up.

Back in Powershell (again as admin), enable RDP with:

    $obj = Get-WmiObject -Class "Win32_TerminalServiceSetting" -Namespace root\cimv2\terminalservices
    $obj.SetAllowTsConnections(1,1)

Allow users to execute scripts with:

    Set-ExecutionPolicy -ExecutionPolicy Unrestricted

Disable the pagefile and shrink it with:

    $System = GWMI Win32_ComputerSystem -EnableAllPrivileges
    $System.AutomaticManagedPagefile = $False
    $System.Put()

    $CurrentPageFile = gwmi -query "select * from Win32_PageFileSetting where name='c:\\pagefile.sys'"
    $CurrentPageFile.InitialSize = 512
    $CurrentPageFile.MaximumSize = 512
    $CurrentPageFile.Put()

Note that this (the pagefile change) is not strictly necessary, but will make
the final image *much* smaller. You like saving network bandwidth and making
devs' lives easier, right?

At this point, it may be worth running Windows Update to ensure there are no
critical issues. Since you just downloaded the VM, that should be unlikely,
but... _shrugs_ it's worth checking.

If you do so (or even if not, your choice), clean up the SXS crud with:

    Dism.exe /online /Cleanup-Image /StartComponentCleanup /ResetBase
    rm C:\Windows\Logs\DISM\dism.log

At this point, another reboot is probably a good idea. You're on Windoze,
rebooting whenever you do anything out of the standard usage flow is generally
a good idea.

Now, you'll want to download Visual Studio 2013. Note that other versions are
not currently supported by our build process. You should be able to find a free
download on Microsoft's website. At the time of this writing, visualstudio.com
had some broken JavaScript that prevented loading the correct page when
browsing within a VM. (This link)
[https://go.microsoft.com/fwlink/?LinkId=532495&clcid=0x409] should work, in
case you have the same issue. Either way, go grab some Philz while you wait,
this install process is gonna take a while.

When you get back with your coffee and the install is still ongoing, you can
take this opportunity to modify the Vagrantfile in the `package` folder of this
directory: `config.vm.base_mac` should be changed to the value shown in your
VirtualBox settings (In VirtualBox, right-click the machine, go to "settings"
then "Network").

After the install is (finally) done, you'll want to install
(Qt)[http://download.qt.io/official_releases/online_installers/qt-unified-windows-x86-online.exe]
so we can use the `qmake` binary. Do so, making sure to skip the signin option
and disable the "open every file with Qt Creator" option. Do not launch Qt
Creator.

Then, you'll want to install
(OpenJDK)[https://bitbucket.org/alexkasko/openjdk-unofficial-builds/downloads/openjdk-1.7.0-u80-unofficial-windows-i586-installer.zip] to `C:\Java` with all
the options set (eg. global env vars).

_Now_, you'll need to install the
(Windows SDK)[https://download.microsoft.com/download/A/6/A/A6AC035D-DA3F-4F0C-ADA4-37C8E5D34E3D/winsdk_web.exe]. It may throw some errors related to not
having the RTM .Net Framework 4, but installing that library causes more
problems than it fixes. Ignore the errors, we won't need any of the SDK
features which rely on that library anyway. Make sure to uncheck "Windows
Native Code Development/Tools" -- despite it not explaining so in the
installer, it depends on the RTM Framework. You can also kill the "Samples" and
all the "Common Utilities" to save space.

Once this is all done, use

    VBoxManage export "VM-NAME" -o box.ovf

to create `box.ovf` and `box-disk1.vmdk` files in the package directory. This
is gonna take a while, since its compressing your entire (likely many GB) disk
image.

Sadly, we don't actually want this: they export with sh*tty compression, and
the compression process we'll be going through to package the Vagrant box works
much more efficiently on unencrypted boxes. Fun fact: the `box.ovf` file is
written almost instantly, the rest of the process time being spent compressing
and writing the `vmdk`. It would save us a bunch of time if we could break
immediately after creating the `ovf`... but killing the export process
automagically runs a cleanup that deletes the `ovf`. So, you'll just have to
wait the hour-or-so for the `box-disk1.vmdk` file to be created.

Now delete it. Replace it with the original one (eg. mine was
`~/VirtualBox VMs/IE8 - Win7/IE8 - Win7-disk1.vmdk`).

Finally, in the package directory, run

    tar --lzma -cvf aerofs-windows.box *

to create the final Vagrant box.

We currently store this box in S3, in the vagrant.arrowfs.org box. This allows
us to access it by hitting `vagrant.arrowfs.org/aerofs-windows.box` (when on
VPN).
