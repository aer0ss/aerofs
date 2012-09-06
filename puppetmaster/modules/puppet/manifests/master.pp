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
    content => template("puppet/kickall.erb"),
  }

  file { "/usr/local/bin/kick":
    mode => 755,
    source => "puppet:///modules/puppet/kick"
  }

  package { "bsd-mailx":
    ensure => present
  }
}
