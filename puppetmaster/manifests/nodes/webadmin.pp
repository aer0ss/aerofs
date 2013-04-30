node "webadmin.aerofs.com" inherits default {

    class{"webadmin":
        STRIPE_PUBLISHABLE_KEY => hiera("stripe_publishable_key"),
        STRIPE_SECRET_KEY => hiera("stripe_secret_key"),
        require => Exec["apt-get update"]
    }
    include webadmin::nginx

    users::add_user {
        [ hiera('dev_users') ]:
    }
}
