node /^config\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }
    include static-config
    include static-config::nginx
    include public-deployment-secret
}
