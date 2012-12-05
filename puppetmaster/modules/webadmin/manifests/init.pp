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
    #
    # TODO puppet should configure nginx (the deb package currently does this).
    #

    file {"/etc/nginx/certs":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "0400",
    }
     file {"/etc/nginx/certs/ssl.key":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.key",
        require => File["/etc/nginx/certs"]
    }

    file {"/etc/nginx/certs/ssl.cert":
        ensure  => present,
        owner   => "root",
        group   => "root",
        mode    => "0400",
        source  => "puppet:///aerofs_ssl/ssl.cert",
        require => File["/etc/nginx/certs"]
    }

    package{"aerofs-web":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
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
