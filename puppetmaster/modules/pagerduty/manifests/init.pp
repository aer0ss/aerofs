# == Class: pagerduty
#
# Pagerduty contains all the probes that report to PagerDuty
#
# === Parameters
#
# Document parameters here.
#
#
# === Variables
#
#
# === Examples
#
#  class { pagerduty:
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
class pagerduty {

  package { "aerofs-pagerduty":
    ensure => latest,
    require => Apt::Source["aerofs"],
  }

  cron { "pagerduty cron.daily":
    command => "/opt/aerofs.pagerduty/cron.daily",
    require => Package["aerofs-pagerduty"],
    hour => "14",
    minute => "00"
  }

  cron { "pagerduty cron.hourly":
    command => "/opt/aerofs.pagerduty/cron.hourly",
    require => Package["aerofs-pagerduty"],
    hour => "*",
    minute => "00"
  }
}
