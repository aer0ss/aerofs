class persistent::services (
  $mysql_bind_address = $persistent::params::mysql_bind_address,
  $redis_bind_address = $persistent::params::redis_bind_address,
) inherits persistent::params {

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
    class {'mysql::server':
        config_hash => {
            'bind_address' => $mysql_bind_address,
        },
    }
    # We require this to get rid of extra users, including ones that can cause
    # issues with other accounts we add. For instance ''@'localhost' interferes
    # with 'aerofs_sp'@'localhost'.
    class {'mysql::server::account_security': }

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
    class {'redis::aof':
        redis_bindaddr => $redis_bind_address,
    }

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
