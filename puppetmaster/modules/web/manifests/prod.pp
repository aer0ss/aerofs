class web::prod {
    file {"/opt/web/production.ini":
        source => "puppet:///modules/web/production.ini",
        require => Package["aerofs-web"],
        notify => Service["uwsgi"],
    }
}
