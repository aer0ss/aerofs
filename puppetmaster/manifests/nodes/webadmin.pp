node "webadmin.aerofs.com" inherits default {

    class{"webadmin":
        stripe_publishable_key => hiera("stripe_publishable_key"),
        stripe_secret_key => hiera("stripe_secret_key"),
        require => Exec["apt-get update"]
    }
    include webadmin::nginx

    users::add_user {
        [ hiera('dev_users') ]:
    }
}
