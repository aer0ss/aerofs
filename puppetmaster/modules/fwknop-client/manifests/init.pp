# == Class: fwknop-client
#
# Install the fwknop-client package and copy kssh, kscp and fwknop.key into
# /usr/bin.
#
# === Parameters
#
# Document parameters here.
#
# [*fwknop_hostnames*]
#   The list of hostnames to add to the fwknop.key file.
#
# === Variables
#
# [*fwknop_key*]
#   The key to use in fwknop.key.  This is taken from hiera
#
# === Examples
#
#  class { fwknop-client:
#    fwknop_hostnames => [ 'sp.aerofs.com', 'z.arrowfs.org' ]
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
class fwknop-client (
    $fwknop_hostnames
) {

  package{"fwknop-client":
    ensure => present,
  }

  file{"/usr/bin/kssh":
    ensure => present,
    source => "puppet:///modules/fwknop-client/kssh"
  }

  file{"/usr/bin/kscp":
    ensure => present,
    source => "puppet:///modules/fwknop-client/kscp"
  }

  $fwknop_key = hiera("fwknop_pass")

  file { "/usr/bin/fwknop.key":
    ensure => present,
    content => template("fwknop-client/fwknop.key.erb"),
    require => package["fwknop-client"]
  }
}
