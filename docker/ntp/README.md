This container controls the host's NTP configurations.

For information about timedatectl, see https://wiki.archlinux.org/index.php/Systemd-timesyncd

[CoreOS's page on NTPd](https://coreos.com/docs/cluster-management/setup/configuring-date-and-timezone/)
is not too helpful for we don't use locally hosted NTP servers. timedatectl only depends on the "sntp" client.

"sntp" is not available in Dedian. Hence the container uses "ntpdate" to update the time although the command
has been [deprecated](https://support.ntp.org/bin/view/Dev/DeprecatingNtpdate).
