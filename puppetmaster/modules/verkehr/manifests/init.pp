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
    $subscribe_port = 443
) {
    package { "aerofs-verkehr":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    service { "verkehr":
        ensure => running,
        provider => upstart,
        require => Package["aerofs-verkehr"],
    }

    $aerofs_ssl_dir = hiera("environment","") ? {
        "staging"   => "aerofs_ssl/staging",
        default     => "aerofs_ssl"
    }

    file {"/opt/verkehr/verkehr.key":
        ensure  => present,
        owner   => "verkehr",
        group   => "verkehr",
        mode    => "0400",
        source  => "puppet:///${aerofs_ssl_dir}/verkehr.key",
        require => Package["aerofs-verkehr"],
        notify  => Service["verkehr"]
    }

    file {"/opt/verkehr/verkehr.crt":
        ensure  => present,
        owner   => "verkehr",
        group   => "verkehr",
        mode    => "0400",
        source  => "puppet:///${aerofs_ssl_dir}/verkehr.crt",
        require => Package["aerofs-verkehr"],
        notify  => Service["verkehr"]
    }

    firewall { "500 forward traffic for verkehr subscribers on port 443":
        table   => "nat",
        chain   => "PREROUTING",
        iniface => "eth0",
        dport   => $subscribe_port,
        jump    => "REDIRECT",
        toports => "29438"
    }
}
