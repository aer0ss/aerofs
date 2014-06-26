node /^dryad\.aerofs\.com$/ inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    users::add_user { "pagerduty" : }

    include dryad
    include dryad::nginx
}
