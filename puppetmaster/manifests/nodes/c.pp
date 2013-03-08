node "c.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    users::add_user {"linday":}

    # Include the cmd service
    include cmd

    include dbtools
}
