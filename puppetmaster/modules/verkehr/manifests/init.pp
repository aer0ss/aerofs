# == Class: verkehr
#
# verkehr, certs and firewall setup
#
# === Parameters
#
# Document parameters here.
#
# [subscribe_port]
#   the port on which subscribers connect to verkehr.
#
# === Examples
#
#  class { verkehr:
#    subscribe_port => 80
#  }
#
#  include verkehr
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class verkehr (
) {
    package { "aerofs-verkehr":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    file{"/etc/init.d/verkehr":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-verkehr"],
    }
    
    service { "verkehr":
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/verkehr"],
    }
}
