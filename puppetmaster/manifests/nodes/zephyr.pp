node /^zephyr\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    include zephyr

    class { "zephyr::firewall_rules" :
        port => 443
    }
}

