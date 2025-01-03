node "z.arrowfs.org" inherits default {

  # TODO: pagerduty ssh key

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
    "verkehr df90 pagerduty@verkehr.aerofs.com 22 /dev/xvda1",
    "x df90 pagerduty@x.aerofs.com 22 /dev/vda",
    "dryad df90 pagerduty@dryad.aerofs.com 22 /dev/xvdf",
  ]:
    hour => "14",
    minute => "0",
    require => Class["pagerduty"]
  }

  # Every 10 minutes.
  pagerduty::probe::base{[
    "developers url https://developers.aerofs.com",
    "appliance checkappliance share.aerofs.com bW9uaXRvcjpHM2lYUlRTRllzRkllREszNlRmQVZVYWdWYnBDZHVPcQ==",
    "web url https://www.aerofs.com",
    "web url https://hybrid.aerofs.com/login",
    "pc url https://enterprise.aerofs.com/login",
    "sp url-internalcert https://sp.aerofs.com/sp",
    "api port api.aerofs.com 443",
    "api port api.aerofs.com 4433",
    "api port api.aerofs.com 8084",
    "verkehr port verkehr.aerofs.com 443",
    "zephyr checkzephyr zephyr.aerofs.com 443",
    "x port x.aerofs.com 443",
    "dryad port dryad.aerofs.com 443",
    "dryad url-internalcert https://dryad.aerofs.com/v1.0/status",
    "hpc hpc_check_deployments_status http://enterprise.aerofs.com:8000/hpc_deployments_status",
    "hpc hpc_check_sys_stats http://enterprise.aerofs.com:8000/hpc_server_sys_stats 80 80 80",
  ]:
    hour => "*",
    minute => "*/10",
    require => Class["pagerduty"]
  }
}
