node "z.arrowfs.org" inherits default {

  # TODO: pagerduty ssh key

  class{"hipchat":
    token => bbae0c9263d4a3e0f614c7058eac6d,
  }

  hipchat::periodic{"@all STANDUP TIME":
    from => "Annoying duck",
    hour => "10",
    minute => "15",
    color => "purple"
  }

  users::add_user {
    [ hiera('dev_users') ]:
  }

  class{"pagerduty":
    require => [
        Exec["apt-get update"],
    ]
  }

  ### WARNING ###
  # New services must be added on pagerduty.com. Log into pagerduty.com and
  # under services click Add New Service and use the defaults for everything
  # except name.

  # Daily.
  pagerduty::probe::base{[
    # N.B. clean_defects will trigger at 90% at t=50m, so we shouldn't reach
    # 90% unless something goes wrong.
    "sv df90 pagerduty@sv.aerofs.com 22 /data",
    "verkehr df90 pagerduty@verkehr.aerofs.com 22 /dev/xvda1",
    "x df90 pagerduty@x.aerofs.com 22 /dev/sda",
    "dryad df90 pagerduty@dryad.aerofs.com 22 /dev/xvdf",
  ]:
    hour => "14",
    minute => "0",
    require => Class["pagerduty"]
  }

  # Every 10 minutes.
  pagerduty::probe::base{[
    # Production
    "rocklog url http://rocklog.aerofs.com/",
    "web url http://www.aerofs.com",
    "pc url https://privatecloud.aerofs.com/login",
    "sv url https://sv.aerofs.com/sv_beta/sv",
    "sp url-internalcert https://sp.aerofs.com/sp",
    "api port api.aerofs.com 443",
    "api port api.aerofs.com 4433",
    "api port api.aerofs.com 8084",
    "verkehr port verkehr.aerofs.com 443",
    "zephyr checkzephyr zephyr.aerofs.com 443",
    "x port x.aerofs.com 443",
    "dryad port dryad.aerofs.com 443",
  ]:
    hour => "*",
    minute => "*/10",
    require => Class["pagerduty"]
  }
}
