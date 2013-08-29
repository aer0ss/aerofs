class transient {
    include transient::services

    # --------------
    # Nginx
    # --------------

    file {"/etc/nginx/sites-available/aerofs-transient":
        source => "puppet:///modules/transient/aerofs-transient",
        require => Package["nginx"],
    }

    file{ "/etc/nginx/sites-enabled/aerofs-transient":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-transient",
        require => File["/etc/nginx/sites-available/aerofs-transient"],
    }

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/transient/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # --------------
    # Admin Panel
    # --------------
    
    file {"/opt/web/production.ini":
        source => "puppet:///modules/transient/production.ini",
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
