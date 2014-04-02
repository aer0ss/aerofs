See also: [common instructions for all OSes](common.html).

Use Windows 7 as the base image. Why? it has the [largest market share](http://thenextweb.com/microsoft/2014/03/01/windows-8-1-now-4-30-market-share-windows-8-falls-6-38/) compared to other versions of Windows.

It would be ideal if tests cover both 64-bit and 32-bit Windows.

# Install Cygwin

Install 32-bit cygwin at `C:\cygwin\`, and save cygwin's setup.exe on the desktop. Install things through the cygwin installation wizard.

# Set up sshd

- In cygwin, Install cygrunsrv
- In cygwin (run as administrator):

        ssh-host-config -y
        cygrunsrv -S sshd
