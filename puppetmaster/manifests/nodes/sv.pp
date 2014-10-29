node "sv.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    include public-email-creds

    include mailserver

    class{"analytics":}

    include proguard

    file { "/maps":
        ensure => directory,
        mode   => 2775,
        group  => "admin",
        owner  => "root",
    }

    package { "gnuplot":
        ensure => installed
    }
}
