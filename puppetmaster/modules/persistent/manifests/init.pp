class persistent {
    include persistent::services

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/persistent/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # --------------
    # Nginx
    # --------------

    file {"/etc/nginx/sites-available/aerofs-cfg":
        source => "puppet:///modules/persistent/aerofs-cfg",
        require => Package["nginx"],
    }

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/nginx.sh":
        source => "puppet:///modules/persistent/probes/nginx.sh",
        require => Package["aerofs-sanity"],
    }
}
