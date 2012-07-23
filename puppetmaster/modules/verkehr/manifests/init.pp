# == Class: verkehr
#
# Full description of class verkehr here.
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
#  class { verkehr:
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
class verkehr {

    package { "aerofs-verkehr":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    $IPT_VERKEHR="/etc/ipt_rules_verkehr.sh"

    file { "${IPT_VERKEHR}":
        ensure  => file,
        source  => "puppet:///modules/verkehr/ipt_rules_verkehr.sh",
        owner   => "root",
        group   => "root",
        mode    => 0700,
        require => Package["aerofs-verkehr"],
        notify  => Exec["${IPT_VERKEHR}", "append iptrules_verkehr to network"],
    }

    exec { "${IPT_VERKEHR}":
        refreshonly => true,
        command     => "/bin/sh ${IPT_VERKEHR}",
        require     => File["${IPT_VERKEHR}"],
    }

    exec { "append iptrules_verkehr to network":
        refreshonly => true,
        command     => "/bin/echo 'pre-up ${IPT_VERKEHR}' >> /etc/network/interfaces",
        require     => File["${IPT_VERKEHR}"],
    }

    #    service { "verkehr":
    #    ensure     => running,
    #    enable     => true,
    #    hasstatus  => true,
    #    hasrestart => true,
    #}
}
