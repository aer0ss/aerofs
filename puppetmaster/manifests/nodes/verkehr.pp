node /^verkehr\.aerofs\.com$/ inherits default {

    include verkehr
    include verkehr::ssl
    include verkehr::firewall

    users::add_user {
        [ hiera('dev_users') ]:
    }

    logrotate::log{"verkehr":
        filename => "/var/log/verkehr/verkehr.log",
        quantity => 14,
        frequency => "daily",
        compress => true,
        require => Class["verkehr"]
    }
}
