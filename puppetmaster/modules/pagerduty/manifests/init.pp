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
#  class { "pagerduty": }
#  pagerduty::probe::base{"url http://www.aerofs.com":}
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
}
