
node "c.aerofs.com" inherits default {
include cmd
include verkehr

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
