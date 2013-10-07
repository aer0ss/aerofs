node /^x\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    users::add_user { "pagerduty" : }

    class { "ejabberd":
        mysql_password => hiera("mysql_password"),
    }

    include ejabberd::ssl

    # firewall ensures a firewall exists
    # ejabberd::firewall_rules just provides the mapping rule
    include firewall
    class { "ejabberd::firewall_rules" :
        port => 443,
        ip_address => "${ipaddress_eth0}/32"
    }
}
