class pd-cfg-ca {

    include private-common
    include ca

    package { "aerofs-config":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    package { [
        "php5-common",
        "php5-cli",
        "php5-fpm"
        ]:
        ensure => latest,
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

    file {"/etc/nginx/sites-available/cfg-server":
        source => "puppet:///modules/pd-cfg-ca/cfg-server",
        require => Package["nginx"],
    }

    # Bootstrap things.
    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-cfg-ca/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }
}
