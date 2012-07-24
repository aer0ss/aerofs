node "z.arrowfs.org" inherits default {

  $fwknop_hostnames = [
    "sp.aerofs.com",
    "x.aerofs.com",
    "x1.aerofs.com"
  ]

  $fwknop_key = hiera("fwknop_pass")

  file { "/opt/aerofs.pagerduty/fwknop.key":
    ensure => present,
    content => template("pagerduty/fwknop.key.erb"),
    require => Class["pagerduty"]
  }

  class{"pagerduty":}
}
