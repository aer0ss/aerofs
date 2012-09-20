node "zephyr.aerofs.com" inherits default {

    users::add_user {
      [ hiera('dev_users') ]:
    }

    include zephyr

    # Firewall rules require ordering, 500 is middle of the road.
    firewall { "500 forward traffic to zephyr from 443":
        table   => "nat",
        chain   => "PREROUTING",
        iniface => "eth0",
        dport   => "443",
        jump    => "REDIRECT",
        toports => "8888"
    }
}
