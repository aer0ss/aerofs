# == Class: logrotate
#
# logrotate sets up a log rotation schedule.
# The class logrotate sets up the daemon.  logrotate::log is used to configure
# each log that should be rotated as well as how often and how long old logs should
# be kept
#
# === Parameters
#
# === Variables
#
#
# === Examples
#
#  include logrotate
#
#  logrotate::log {"sp":
#    filename => "/data/var/log/sp.log",
#    quantity => 14,
#    frequency => "daily",
#    compress => true
#  }
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class logrotate {

    package{"logrotate":
        ensure => present,
    }

    file { "/etc/logrotate.d":
        ensure => directory,
        owner => root,
        group => root,
        mode => 755,
        require => Package["logrotate"],
    }

    file { "/etc/cron.hourly/logrotate":
      ensure => "link",
      target => "/etc/cron.daily/logrotate",
    }
}
