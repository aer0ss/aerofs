node "privatecloud.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    # Include license website
    include lizard
    # Include nginx configurations
    include lizard::nginx
}
