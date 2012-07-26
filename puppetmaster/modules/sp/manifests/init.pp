# == Class: sp
#
# Full description of class sp here.
#
# === Parameters
#
# Document parameters here.
#
# [*sample_parameter*]
#   Explanation of what this parameter affects and what it defaults to.
#   e.g. "Specify one or more upstream ntp servers as an array."
#
# === Variables
#
# Here you should define a list of variables that this module would require.
#
# [*sample_variable*]
#   Explanation of how this variable affects the funtion of this class and if it
#   has a default. e.g. "The parameter enc_ntp_servers must be set by the
#   External Node Classifier as a comma separated list of hostnames." (Note,
#   global variables should not be used in preference to class parameters  as of
#   Puppet 2.6.)
#
# === Examples
#
#  class { sp:
#    servers => [ 'pool.ntp.org', 'ntp.local.company.com' ]
#  }
#
# === Authors
#
# Author Name <author@domain.com>
#
# === Copyright
#
# Copyright 2011 Your name here, unless otherwise noted.
#
class sp {

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

        file {"/data/var/svlogs_staging":
            ensure  => directory,
            owner   => "tomcat6",
            group   => "adm",
            mode    => "0750",
            require => [ File["/data/var/log"], User["tomcat6"] ],
        }

        file {"/data/var/svlogs_prod":
            ensure  => directory,
            owner   => "tomcat6",
            group   => "adm",
            mode    => "0750",
            require => [ File["/data/var/log"], User["tomcat6"] ],
        }

        file {"/data/var/log/tomcat6":
            ensure  => directory,
            owner   => "tomcat6",
            group   => "adm",
            mode    => "0750",
            require => [ File["/data/var/log"], User["tomcat6"] ],
        }

        file { "/var/svlogs_prod":
            ensure => directory,
        }

        mount { "/var/svlogs_prod":
            atboot  => true,
            device  => "/data/var/svlogs_prod",
            ensure  => mounted,
            fstype  => "none",
            options => "bind",
            require => File["/data/var/svlogs_prod","/var/svlogs_prod"],
        }

        file { "/var/svlogs_staging":
            ensure => directory,
        }

        mount { "/var/svlogs_staging":
            atboot  => true,
            device  => "/data/var/svlogs_staging",
            ensure  => mounted,
            fstype  => "none",
            options => "bind",
            require => File["/data/var/svlogs_staging","/var/svlogs_staging"],
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

    define proguard() {
        package { "proguard":
            ensure => installed,
        }
    }
}
