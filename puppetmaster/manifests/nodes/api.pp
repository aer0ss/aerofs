node /^api\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    include havre
    include bifrost
}
