class puppet::master::config inherits puppet::config {

  File["/etc/puppet/auth.conf"]{
    content => template("puppet/master-auth.conf.erb")
  }

  file{"/etc/puppet/tagmail.conf":
    ensure => present,
    source => "puppet:///modules/puppet/tagmail.conf",
  }
}
