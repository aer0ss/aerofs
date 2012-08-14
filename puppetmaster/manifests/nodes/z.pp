node "z.arrowfs.org" inherits default {

  # TODO: pagerduty ssh key

  users::add_user {
    [ hiera('dev_users') ]:
  }

  $fwknop_hostnames = [
    "sp.aerofs.com",
    "x.aerofs.com",
    "x1.aerofs.com",
    "verkehr.aerofs.com",
  ]

  class{"fwknop-client":
    fwknop_hostnames => $fwknop_hostnames
  }

  class{"pagerduty":
    require => [
        Class["fwknop-client"],
        Exec["apt-get update"],
    ]
  }

  #daily
  pagerduty::probe::base{[
    "df90 pagerduty@sp.aerofs.com 22 /dev/xvda1",
    "df90 pagerduty@verkehr.aerofs.com 22 /dev/xvda1",
    "df90 pagerduty@x1.aerofs.com 22 /dev/sda1",
    "df90 pagerduty@x1.aerofs.com 22 /dev/sdf1",
    "df90 pagerduty@x.aerofs.com 22 /dev/xvda"
  ]:
    hour => "14",
    minute => "0",
    require => Class["pagerduty"]
  }

  #hourly
  pagerduty::probe::base{[
    # Production
    "url http://www.aerofs.com",
    "url https://sv.aerofs.com/sv_beta/sv",
    "port reloadedsp.aerofs.com 443",
    "port verkehr.aerofs.com 443",
    "port zephyr.aerofs.com 443",
    "port x.aerofs.com 443",
    # Staging
    "port sp.aerofs.com 443",
    "port sp.aerofs.com 80",
    # Meta (not used right now)
    #"ping z.arrowfs.org"
  ]:
    hour => "*",
    minute => "*/10",
    require => Class["pagerduty"]
  }
}
