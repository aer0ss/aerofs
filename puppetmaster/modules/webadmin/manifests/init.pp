# == Class: webadmin
#
# The webadmin module for aerofs
#
# === Parameters
#
# === Variables
#
# === Examples
#
#   include webadmin
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class webadmin {
    package{"aerofs-web":
        ensure => latest,
        require => Apt::Source["aerofs"]
    }
}
