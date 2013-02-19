node "vpn.arrowfs.org" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    users::add_user { "linday": }

    include vpn
}
