See also: [common instructions for all OSes](common.html).

Use Windows 7 as the base image. Why? it has the [largest market share](http://thenextweb.com/microsoft/2014/03/01/windows-8-1-now-4-30-market-share-windows-8-falls-6-38/) compared to other versions of Windows.

It would be ideal if tests cover both 64-bit and 32-bit Windows.

# Create the VM

- 1024MB RAM
- 40GB VMDK virtual drive
- Disable Widows Firewall
- Disable Windows Update as vagrant does not take kindly to the long wait at start/stop time and the gratuitous reboots
- Disable UAC to avoid needless troubles (type "uac" in the start menu to find the settings)


# Install Cygwin and packages

Install 32-bit cygwin at `C:\cygwin\`, and save cygwin's setup.exe on the desktop. Install things through the cygwin installation wizard. In Cygwin, install the following packages:

- cygrunsrv
- openssh
- rsync

Note 2: Run Cygwin as administrator when installing pip.


Note: DO NOT install Python for Windows which doesn't recognize UNIX paths. Install Python via Cygwin instead.


# Configure sshd

- In cygwin (run as administrator):

        ssh-host-config 

   When prompted:
   
   - overwrite both files
   - use privilege separation
   - enter "tty ntsec" for the CYGWIN value
   - don't use a different name
   - use "temp123" as a password

- Edit `/etc/sshd_config`, add `UseDNS no` to the file. Without this flag remote logins may be slow due to reverse DNS lookup.

- Then start the service:

        cygrunsrv -S sshd

- Similar to sshd on Linux, copy pubkeys to `~/.ssh/authorized_keys` as necessary to enable password-less logins.
