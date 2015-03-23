class bunker { # FIXME (AG): should I allow a port to be specified as an argument?
    # so bunker can use the MySQL-python package
    package{"libmysqlclient-dev":
        ensure => latest
    }

    package{"aerofs-bunker":
        ensure => latest,
        require => Apt::Source["aerofs"],
    }

    file{"/etc/init.d/bunker":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-bunker"],
    }

    logrotate::log{"bunker":
        # intentionally left blank
    }

    service{"bunker":
        enable => true,
        ensure => running,
        provider => upstart,
        require => [
            File["/etc/init.d/bunker"],
            File["/opt/bunker/production.ini"],
        ],
    }

    file {"/opt/bunker/production.ini":
        source => "puppet:///modules/bunker/production.ini",
        require => Package["aerofs-bunker"],
        notify => Service["bunker"]
    }
}
