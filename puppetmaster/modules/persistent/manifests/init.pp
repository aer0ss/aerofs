class persistent {

    include private-common
    include ca::autostart

    package { "aerofs-config":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/persistent/bootstrap.tasks",
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

    # --------------
    # Cfg/CA Server
    # --------------

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
        require => Package["php5-fpm"],
    }

    package{"nginx":
        ensure => present,
    }
    file{"/etc/nginx/certs":
        ensure => directory,
        require => Package["nginx"]
    }
    file {"/etc/nginx/sites-available/aerofs-cfg":
        source => "puppet:///modules/persistent/aerofs-cfg",
        require => Package["nginx"],
    }
    file{ "/etc/nginx/sites-enabled/aerofs-cfg":
        ensure  => link,
        target  => "/etc/nginx/sites-available/aerofs-cfg",
        require => File["/etc/nginx/sites-available/aerofs-cfg"],
    }

    # --------------
    # MySQL
    # --------------

    # MySQL client and MySQL server.
    include mysql
    include mysql::server

    # Should get pulled via apt dependency, but add it here just for good
    # measure.
    package {"aerofs-spdb":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    # --------------
    # Redis
    # --------------

    # Redis in AOF (append only file) mode.
    include redis::aof

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/nginx.sh":
        source => "puppet:///modules/persistent/probes/nginx.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/ca.sh":
        source => "puppet:///modules/persistent/probes/ca.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/mysql.sh":
        source => "puppet:///modules/persistent/probes/mysql.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/redis.sh":
        source => "puppet:///modules/persistent/probes/redis.sh",
        require => Package["aerofs-sanity"],
    }

    # --------------
    # Disable auto-start
    # --------------

    Service <| tag == 'autostart-overridable' |> {
        ensure => stopped,
    }
}
