# == Class: devman
#
# === Parameters
#
# === Examples
#
#  include devman
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2013 Air Computing Inc, unless otherwise noted.
#
class devman (
) {
    package { "aerofs-devman":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    service { "devman":
        ensure => running,
        provider => upstart,
        require => Package["aerofs-devman"],
    }
}
