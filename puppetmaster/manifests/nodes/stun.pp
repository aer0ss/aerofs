node "stun.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    include stun
}
