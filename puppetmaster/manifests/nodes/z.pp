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
    "x df90 pagerduty@x.aerofs.com 22 /dev/xvda",
    "sss df90 pagerduty@sss.aerofs.com 22 /dev/xvda1"
  ]:
    hour => "14",
    minute => "0",
    require => Class["pagerduty"]
  }

  #sss only (temp)
  pagerduty::probe::base{"morning sss probe":
    command => "sss df90 pagerduty@sss.aerofs.com 22 /dev/xvda1",
    hour => "9",
    minute => "0",
    require => Class["pagerduty"]
  }
  pagerduty::probe::base{"evening sss probe":
    command => "sss df90 pagerduty@sss.aerofs.com 22 /dev/xvda1",
    hour => "19",
    minute => "0",
    require => Class["pagerduty"]
  }

  #hourly
  pagerduty::probe::base{[
    # Production
    "web url http://www.aerofs.com",
    "sv url https://sv.aerofs.com/sv_beta/sv",
    "sss url https://sss.aerofs.com/syncstat 10",
    "sp url https://sp.aerofs.com/sp",
    "verkehr port verkehr.aerofs.com 443",
    "zephyr port zephyr.aerofs.com 443",
    "x port x.aerofs.com 443",
    "download-page url https://www.aerofs.com/download?a=WWtheWise\&b=0",
    # Staging
    "staging port staging.aerofs.com 443",
    "staging port staging.aerofs.com 80",
  ]:
    hour => "*",
    minute => "*/10",
    require => Class["pagerduty"]
  }
}
