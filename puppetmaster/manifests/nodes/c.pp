node "c.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    users::add_user {"linday":}

    include cmd
    include dbtools
}
