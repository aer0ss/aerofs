class web (
    $stripe_publishable_key,
    $stripe_secret_key,
    $uwsgi_port = 8080
) {
    $STRIPE_PUBLISHABLE_KEY = $stripe_publishable_key
    $STRIPE_SECRET_KEY = $stripe_secret_key

    package{"aerofs-web":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    service{"uwsgi":
        ensure => running,
        hasstatus => false,
        hasrestart => true,
        require => [
            Package["aerofs-web"],
            File["/var/www"]
        ]
    }

    file{"/var/www":
        ensure => directory,
        owner  => "www-data",
    }

    file{"/etc/uwsgi/apps-enabled/aerofs.ini":
        content => template("web/aerofs.ini.erb"),
        owner => root,
        group => root,
        mode => "644",
        require => Package["aerofs-web"],
        notify => Service["uwsgi"]
    }
    logrotate::log{"web": }
}
