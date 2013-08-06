class webadmin::prod {
    file {"/opt/web/production.ini":
        source => "puppet:///modules/webadmin/production.ini",
        require => Package["aerofs-web"],
        notify => Service["uwsgi"],
    }
}
