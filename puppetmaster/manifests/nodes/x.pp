node /^x\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    users::add_user { "pagerduty" : }

    class { "ejabberd":
        mysql_password => hiera("mysql_password"),
    }

    class { "ejabberd::firewall_rules" :
        port => 443,
        ip_address => "${ipaddress_eth0}/32"
    }

    include zephyr

    class { "zephyr::firewall_rules" :
        port => 443,
        ip_address => "${ipaddress_eth0_0}/32"
    }
}
