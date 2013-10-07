node /^zephyr\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    include zephyr

    # firewall provides the firewall
    # zephyr::firewall_rules provides the rules
    include firewall
    class { "zephyr::firewall_rules" :
        port => 443
    }
}

