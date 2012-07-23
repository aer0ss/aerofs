# == Class: fwknop
#
# Full description of class fwknop here.
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
#  class { fwknop:
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
class fwknop($key) {

    package { 'fwknop-server':
        ensure => installed,
    }

    file { '/etc/fwknop/access.conf':
        ensure  => file,
        content => template("fwknop/access.conf.erb"),
        owner   => 'root',
        group   => 'root',
        mode    => 0600,
        notify  => Service['fwknop-server'],
        require => Package['fwknop-server'],
    }

    file { '/etc/default/fwknop-server':
        ensure  => file,
        source  => "puppet:///modules/fwknop/fwknop-server.default",
        owner   => 'root',
        group   => 'root',
        mode    => '0644',
        require => Package['fwknop-server'],
        notify  => Service['fwknop-server'],
    }

    $IPT = "/etc/ipt_rules_puppet.sh"

    file { "${IPT}":
        ensure  => file,
        source  => "puppet:///modules/fwknop/ipt_rules_puppet.sh",
        owner   => 'root',
        group   => 'root',
        mode    => 0700,
        require => Package['fwknop-server'],
        notify  => Exec["${IPT}", "append iptrules to network"],
    }

    exec { "${IPT}":
        refreshonly => true,
        command     => "/bin/sh ${IPT}",
        require     => File["${IPT}"],
    }

    exec { "append iptrules to network":
        refreshonly => true,
        command     => "/bin/echo 'pre-up ${IPT}' >> /etc/network/interfaces",
        require     => File["${IPT}"],
    }

    service { 'fwknop-server':
        ensure     => running,
        enable     => true,
        hasstatus  => true,
        hasrestart => true,
    }

}
