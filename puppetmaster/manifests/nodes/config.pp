node /^config\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    package { "aerofs-config":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }
}
