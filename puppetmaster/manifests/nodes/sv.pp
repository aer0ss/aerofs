node "sv.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    # install syncstat servlet
    class{"servlet::sv":
        mysql_password => hiera("mysql_password"),
        mysql_endpoint => hiera("mysql_endpoint")
    }

    include mailserver
}
