class webadmin::prod inherits webadmin(
    $stripe_publishable_key,
    $stripe_secret_key,
    $uwsgi_port = 8080
) {
    file {"/opt/web/production.ini":
        source => "puppet:///modules/webadmin/production.ini",
        require => Package["aerofs-web"],
        notify => Service["uwsgi"],
    }
}
