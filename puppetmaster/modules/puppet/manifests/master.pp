class puppet::master inherits puppet {

  include puppet::master::config

  package { "puppetmaster" :
    ensure => installed,
  }

  service { "puppetmaster" :
    ensure => running,
    subscribe => Class["puppet::config"]
  }
}
