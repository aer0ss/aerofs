Maintaining the gerrit computer & wall displays
===

The Mac BookPro laptop that hosts the gerrit VM is the same computer that feeds the three wall displays in the main office.

**Input**: The laptop's keyboard and mouse are broken. Use the external keyboard-mouse combo located by the laptop instead.

**Output**: The laptop connects to the three displays using three USB-DVI adaptors. The laptop is set not to sleep while the lid is closed.

**Ports**: The laptop's DVI port is broken. Don't use it to connect to additional monitors.

**Gerrit**: Gerrit runs in a VirtualBox VM under the user account 'github.' A cron job in the VM reboots the VM once a day to avoid gerrit downtimes we experienced in the past.

**Displayed content**: The three displays show JIRA Engineering Wallboard, JIRA Support Dashboard, and CI status, respectively, 24 hours a day.

## What if gerrit is inaccessible

The simplest thing to do is to reboot the physical laptop. The laptop is too old to be 99.999% available.

If possible, **flush and/or shutdown the gerrit VM before rebooting the host** to avoid data loss and corruption. To do so, switch to the user account 'github' using the password stored in Air Computing Team/software/creds.txt.

## What to do after the laptop is rebooted

1. Log in to user account 'github' using the password stored in Air Computing Team/software/creds.txt.

2. Make sure Tunnbelblick is connected to the VPN.

3. Launch VirtualBox and its 'gerrit' VM.

4. Switch to user account 'aerofs' using password 'temp123'

5. On the left-most display, launch Google Chrome, go to

    `https://aerofs.atlassian.net/plugins/servlet/Wallboard/?dashboardId=10702`

   Log in using JIRA account `yuri+jirazendesk@aerofs.com` and the password in Air Computing Team/software/creds.txt.

   Zoom in the page for an sufficient line width in the burn-down chart.

6. On the central display, launch Safari (instead of Google Chrome, since otherwise the zooming at step 4 would also affect this display). Go to:

    `https://aerofs.atlassian.net/secure/Dashboard.jspa?selectPageId=10705`
    
    Log in using the same account as in step 4.
    
7. On the right-most display, open a new Safari window and go to:

    `https://newci.arrowfs.org:8543/`

    Log in using an arbitrary user account on CI.

## Troubleshooting

- What if the laptop can't access `newci.arrowfs.org`?

    It's likely that the VPN is not working. Try restarting Tunnelblick or the laptop.





