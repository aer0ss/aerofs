class webadmin::prod (
    $stripe_publishable_key,
    $stripe_secret_key,
    $uwsgi_port = 8080
) inherits webadmin {
    file {"/opt/web/production.ini":
        source => "puppet:///modules/webadmin/production.ini",
        require => Package["aerofs-web"],
        notify => Service["uwsgi"],
    }
}
