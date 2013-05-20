class pd-app-persistent {

    include private-common
    include ca

    package { "aerofs-config":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    # Bootstrap things.
    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-app-persistent/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # Links so that bootstrap pulls config without the web service running.
    exec{"rm configuration.properties":
        command => "/bin/rm -f /opt/bootstrap/configuration.properties",
        require => Package["aerofs-bootstrap"],

    }
    file{ "/opt/bootstrap/configuration.properties":
        ensure  => link,
        target  => "/opt/config/properties/server.properties",
        require => Exec["rm configuration.properties"],
    }

    package { [
        "php5-common",
        "php5-cli",
        "php5-fpm"
        ]:
        ensure => latest,
    }

    service { "php5-fpm":
        ensure => running,
        enable => true,
        hasstatus => true,
        hasrestart => true,
    }

    # The configuration service and the CA server both use nginx. Do not enable
    # the configuration service right away, since we have to setup the CA server
    # first.
    package{"nginx":
        ensure => present,
    }
    file{"/etc/nginx/certs":
        ensure => directory,
        require => Package["nginx"]
    }
    file {"/etc/nginx/sites-enabled/cfg-server":
        source => "puppet:///modules/pd-app-persistent/cfg-server",
        require => Package["nginx"],
    }
}
