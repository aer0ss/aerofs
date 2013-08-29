class unified {
    include persistent::services
    include transient::services

    # --------------
    # Nginx
    # --------------

    file {"/etc/nginx/sites-available/aerofs-unified":
        source => "puppet:///modules/unified/aerofs-unified",
        require => Package["nginx"],
    }

    file{ "/etc/nginx/sites-enabled/aerofs-unified":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-unified",
        require => File["/etc/nginx/sites-available/aerofs-unified"],
    }

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/unified/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # --------------
    # Admin Panel
    # --------------
    
    file {"/opt/web/production.ini":
        source => "puppet:///modules/unified/production.ini",
        require => Package["aerofs-web"],
        notify => Service["uwsgi"],
    }

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/nginx.sh":
        source => "puppet:///modules/transient/probes/nginx.sh",
        require => Package["aerofs-sanity"],
    }
}
