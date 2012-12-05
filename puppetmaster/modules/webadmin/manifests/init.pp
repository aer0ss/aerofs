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
        require => Apt::Source["aerofs"],
    }

    # PH nginx gets installed and configured implicitly by aerofs-web
    # TODO Move configuration into puppet.
    service{"nginx":
        ensure => running,
        require => Package["aerofs-web"]
    }

    service{"uwsgi":
        ensure => running,
        require => [
            Package["aerofs-web"],
            File["/var/www"]
        ]
    }

    file{"/var/www":
        ensure => directory,
        owner  => "www-data",
    }
}
