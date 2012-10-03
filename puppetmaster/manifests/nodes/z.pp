node "z.arrowfs.org" inherits default {

  # TODO: pagerduty ssh key

  class{"hipchat":
    token => bbae0c9263d4a3e0f614c7058eac6d,
  }

  hipchat::periodic{"@all STANDUP TIME":
    from => "Annoying duck",
    hour => "12",
    minute => "45",
    color => "purple"
  }

  users::add_user {
    [ hiera('dev_users') ]:
  }

  $fwknop_hostnames = [
    "sp.aerofs.com",
    "x.aerofs.com",
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

  ### WARNING ###
  # New services must be added on pagerduty.com. Log into pagerduty.com and under Services
  # click Add New Service and use the defaults for everything except name.

  #daily
  pagerduty::probe::base{[
    "sv df90 pagerduty@sv.aerofs.com 22 /dev/xvda1",
    "verkehr df90 pagerduty@verkehr.aerofs.com 22 /dev/xvda1",
    "x df90 pagerduty@x.aerofs.com 22 /dev/xvda"
  ]:
    hour => "14",
    minute => "0",
    require => Class["pagerduty"]
  }

  #hourly
  pagerduty::probe::base{[
    # Production
    "web url http://www.aerofs.com",
    "sv url https://sv.aerofs.com/sv_beta/sv",
    "sss url https://sss.aerofs.com/syncstat",
    "sp url https://reloadedsp.aerofs.com/sp",
    "verkehr port verkehr.aerofs.com 443",
    "zephyr port zephyr.aerofs.com 443",
    "x port x.aerofs.com 443",
    # Staging
    "staging port sp.aerofs.com 443",
    "staging port sp.aerofs.com 80",
  ]:
    hour => "*",
    minute => "*/10",
    require => Class["pagerduty"]
  }
}
