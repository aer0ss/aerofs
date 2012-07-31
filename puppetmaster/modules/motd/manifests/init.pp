# == Class: motd
#
# Custom Aerofs MOTD
#
# === Parameters
#
#
# === Variables
#
#
# === Examples
#
# include motd
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class motd {
    file{"/etc/motd":
        ensure => present,
        source => "puppet:///modules/motd/motd"
    }
}
