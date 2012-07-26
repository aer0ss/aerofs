# == Class: syncstat
#
# This is the syncstat module
#
# === Parameters
#
# === Variables
#
# === Examples
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright (c) 2012, Air Computing Inc. All rights reserved.
#
class syncstat {

    define mount() {
        file { "/data/var":
            ensure  => directory,
            owner   => root,
            group   => root,
            require => Mount["/data"],
        }

        file {"/data/var/log":
            ensure  => directory,
            owner   => root,
            group   => root,
            require => File["/data/var"],
        }

        file {"/data/var/log/tomcat6":
            ensure  => directory,
            owner   => "tomcat6",
            group   => "adm",
            mode    => "0750",
            require => [ File["/data/var/log"], User["tomcat6"] ],
        }

        file { "/var/log/tomcat6":
            ensure => directory,
        }

        mount { "/var/log/tomcat6":
            atboot  => true,
            device  => "/data/var/log/tomcat6",
            ensure  => mounted,
            fstype  => "none",
            options => "bind",
            require => File["/data/var/log/tomcat6","/var/log/tomcat6"],
        }
    }
}
