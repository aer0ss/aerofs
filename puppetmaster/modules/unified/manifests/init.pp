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

    # Use upstart for nginx; the upstart dependency mechanisms let us
    # gracefully handle ordering on system startup.
    # Replacing this file will cause nginx to be restarted (reflecting
    # the fact that it is now upstart's responsibility)
    file { "/etc/init/nginx.conf":
        ensure => present,
        owner => "root",
        group => "root",
        mode => "644",
        source => "puppet:///modules/unified/nginx/nginx.conf",
        require => Package["nginx"],
        notify => Exec["nginx-restart-as-upstart-job"],
    }

    # This target uses the old SysV init script to stop nginx, then 
    # starts the service as an Upstart job. If we don't stop the SysV
    # service before replacing the SysV init script, we will orphan
    # those processes (initctl and SysV will both refuse to stop them)
    # Only needed as a prereq of replacing /etc/init.d/nginx
    #   (Puppet doesn't handle this kind of thing very well.)
    exec {"nginx-restart-as-upstart-job":
        command => "/etc/init.d/nginx stop && initctl start nginx",
        refreshonly => true,
        path => ["/usr/sbin", "/sbin", "/bin/", "/usr/bin/"],
    }

    file {"/etc/init.d/nginx":
        ensure  => link,
        target  => "/lib/init/upstart-job",
        require => [
            File["/etc/init/nginx.conf"],
            Exec["nginx-restart-as-upstart-job"],
        ],
    }

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
    # Bunker (Maintenance Panel)
    # --------------

    include bunker

    # --------------
    # Bootstrap
    # --------------

    include bootstrap

    file { "/etc/init/bootstrap-startup.conf":
        ensure => present,
        source => "puppet:///modules/bootstrap/bootstrap-startup.conf",
        require => Package["aerofs-bootstrap"],
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

   file {"/opt/sanity/probes/bunker.sh":
        source => "puppet:///modules/unified/probes/bunker.sh",
        require => Package["aerofs-sanity"],
    }

    # --------------
    # Apt
    # --------------

    file {"/etc/cron.weekly/apt-xapian-index" :
        ensure => absent
    }
    file {"/etc/cron.daily/apt" :
        ensure => absent
    }
}
