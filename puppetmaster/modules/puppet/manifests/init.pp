# == Class: puppet
#
# The puppet module.  The main module is for installing, configuring and
# running the puppet agent.  The puppet::master module is for installing
# configuring and running the puppetmaster.
#
# Note: Since puppet agent and puppet master both use the same config
# files, the puppet::master::config contains configs for both the master
# and the agent.
#
# === Parameters
#
# [*puppetmaster*]
#   The fqdn of the puppet master server.
#   $puppetmaster must be set before the module is included.  Because of
#   the inheritance used, you must include puppet rather than declare it
#   as a class instance.
#
# === Variables
#
#
# === Examples
#
#  include puppet
#
#   -- or --
#
#  include puppet::master
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class puppet {

  include puppet::config

  package { "puppet":
    ensure => present,
  }

  service{"puppet":
    ensure => running,
    enable => true,
    # must use restart, as stop kills the puppet agent before
    # it can call start again
    hasrestart => true,
    require => Package["puppet"],
    subscribe => Class["puppet::config"],
  }
}
