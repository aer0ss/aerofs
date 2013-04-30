node "sp.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    include devman
    include redis::aof
    include jeq

    # install sp servlet
    include servlet
    class{"servlet::config::sp":
        mysql_password => hiera("mysql_password"),
        mysql_endpoint => hiera("mysql_endpoint")
    }
}
