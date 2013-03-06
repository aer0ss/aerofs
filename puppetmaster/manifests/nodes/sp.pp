node "sp.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    include redis::aof
    include jeq

    # install sp servlet
    class{"servlet::sp":
        mysql_password => hiera("mysql_password"),
        mysql_endpoint => hiera("mysql_endpoint")
    }
}
