node "sp.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    # DF - devman disabled until it stops hogging verkehr's sockets.
    #include devman
    include redis::aof
    include jeq

    include public-email-creds

    # install sp servlet
    include servlet
    class{"servlet::config::sp":
        mysql_password => hiera("mysql_password"),
        mysql_endpoint => hiera("mysql_endpoint")
    }
}
