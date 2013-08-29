#
# The services class include all services and configuration on the box EXCEPT
# for nginx and bootstrap configuration (since these services need to be
# configured differently depending on the deployment mode.
#
class persistent::services {

    include private-common
    include ca::autostart

    package { "aerofs-config":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    # --------------
    # DB Backup
    # --------------

    include db-backup

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

    # --------------
    # Nginx
    # --------------

    include nginx-package

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
