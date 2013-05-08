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

    file {"/etc/nginx/sites-available/ca-server":
        source => "puppet:///modules/pd-cfg-ca/ca-server",
        require => Package["nginx"],
    }

    file {"/etc/nginx/sites-available/cfg-server":
        source => "puppet:///modules/pd-cfg-ca/cfg-server",
        require => Package["nginx"],
    }

    file { '/etc/nginx/sites-enabled/ca-server':
        ensure => 'link',
        target => '/etc/nginx/sites-available/ca-server',
    }
}
