node "privatecloud.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    # Include license website
    class { "lizard":
        mysql_password => hiera("mysql_password"),
    }
    # Include nginx configurations
    include lizard::nginx


    # Include mysql (for mysql client)
    include mysql
}
