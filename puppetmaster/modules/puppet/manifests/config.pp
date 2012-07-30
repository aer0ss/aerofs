class puppet::config {

  $listen = true

  file { "/etc/puppet/puppet.conf" :
    content => template("puppet/puppet.conf.erb"),
  }

  file { "/etc/puppet/auth.conf" :
    content => template("puppet/auth.conf.erb")
  }

  # this file is necessary but stupid </rant>
  file { "/etc/default/puppet" :
    ensure => present,
    source => "puppet:///modules/puppet/default_puppet"
  }
}
