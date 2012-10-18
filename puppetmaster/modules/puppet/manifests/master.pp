class puppet::master inherits puppet {

  include puppet::master::config

  package { "puppetmaster" :
    ensure => installed,
  }

  service { "puppetmaster" :
    ensure => running,
    subscribe => Class["puppet::config"]
  }

  file { "/usr/local/bin/kickall":
    mode => 755,
    source => "puppet:///modules/puppet/kickall",
  }

  file { "/etc/puppet/nodes.yaml":
    mode => 666,
    content => template("puppet/nodes.yaml.erb")
  }

  file { "/etc/puppet/do_not_kick.yaml":
    mode => 666,
    ensure => present
  }

  package { "bsd-mailx":
    ensure => present
  }
}
