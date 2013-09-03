class unified {
    # -------------
    # Hostname and /etc/hosts
    # -------------
    file {"/etc/hostname":
        source => "puppet:///modules/unified/hostname",
    }
    file {"/etc/hosts":
        source => "puppet:///modules/unified/hosts",
    }
    file {"/etc/default/grub":
        source => "puppet:///modules/unified/grub-options",
    }
    file {"/etc/network/interfaces":
        source => "puppet:///modules/unified/network-interfaces",
    }

    class {'persistent::services':
        mysql_bind_address => '127.0.0.1',
        redis_bind_address => '127.0.0.1',
    }
    include transient::services

    # --------------
    # Nginx
    # --------------

    # aerofs-ca-server.deb ships a more insecure configuration by default, so
    # we must apply this one afterward.
    file {"/etc/nginx/sites-available/aerofs-ca":
        source => "puppet:///modules/unified/nginx/ca",
        require => Package["nginx", "aerofs-ca-server"],
    }
    file {"/etc/nginx/sites-available/aerofs-cfg":
        source => "puppet:///modules/unified/nginx/cfg",
        require => Package["nginx"],
    }
    file {"/etc/nginx/sites-available/aerofs-service":
        source => "puppet:///modules/unified/nginx/service",
        require => Package["nginx"],
    }
    file {"/etc/nginx/sites-available/aerofs-web":
        source => "puppet:///modules/unified/nginx/web",
        require => Package["nginx"],
    }

    file{ "/etc/nginx/sites-enabled/aerofs-ca":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-ca",
        require => File["/etc/nginx/sites-available/aerofs-ca"],
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
        source => "puppet:///modules/unified/probes/nginx.sh",
        require => Package["aerofs-sanity"],
    }
}
