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
    include enterprise-network-config

    class {'persistent::services':
        mysql_bind_address => '127.0.0.1',
        redis_bind_address => '127.0.0.1',
    }
    include transient::services

    include unified::network

    # --------------
    # Nginx
    # --------------

    # aerofs-ca-server.deb ships a more insecure configuration by default, so
    # we must apply this one afterward.
    file {"/etc/nginx/sites-available/aerofs-ca":
        source => "puppet:///modules/unified/nginx/ca",
        require => Package["nginx", "aerofs-ca-server"],
    }
    file {"/etc/nginx/sites-available/aerofs-cfg-public":
        source => "puppet:///modules/unified/nginx/cfg-public",
        require => Package["nginx"],
    }
    file {"/etc/nginx/sites-available/aerofs-cfg-private":
        source => "puppet:///modules/unified/nginx/cfg-private",
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

    file{ "/etc/nginx/sites-enabled/aerofs-cfg-private":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-cfg-private",
        require => File["/etc/nginx/sites-available/aerofs-cfg-private"],
    }
    file{ "/etc/nginx/sites-enabled/aerofs-ca":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-ca",
        require => File["/etc/nginx/sites-available/aerofs-ca"],
    }

    # --------------
    # Bootstrap
    # --------------

    include bootstrap

    cron { "bootstrap_startup":
        command => "/usr/bin/aerofs-bootstrap-taskfile /opt/bootstrap/tasks/startup.tasks",
        user    => "root",
        special => "reboot",
        environment => "PATH=/usr/sbin:/usr/bin:/sbin:/bin",
        require => Package["aerofs-bootstrap"]
    }

    file { "/etc/aerofs":
        ensure => "directory"
    }

    file { "/etc/aerofs/private-deployment-flag":
        ensure => present,
        require => File["/etc/aerofs"]
    }

    # --------------
    # Admin Panel
    # --------------

    file {"/opt/web/production.ini.template":
        source => "puppet:///modules/unified/production.ini.template",
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
