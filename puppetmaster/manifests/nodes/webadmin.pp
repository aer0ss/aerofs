node "webadmin.aerofs.com" inherits default {

    class{"web":
        stripe_publishable_key => hiera("stripe_publishable_key"),
        stripe_secret_key => hiera("stripe_secret_key"),
        require => Exec["apt-get update"]
    }
    include web::nginx
    include web::prod

    include public-deployment-secret

    users::add_user {
        [ hiera('dev_users') ]:
    }
}
