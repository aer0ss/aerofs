node "webadmin.aerofs.com" inherits default {

    class{"webadmin":
        require => Exec["apt-get update"]
    }

    users::add_user {
        [ hiera('dev_users') ]:
    }
}
