# == Class: redis
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012-2013 Air Computing Inc, unless otherwise noted.
#
class redis {
    apt::key { "dotdeb":
        ensure => present,
        key_source => "http://www.dotdeb.org/dotdeb.gpg"
    }

    apt::source { "dotdeb":
        location    => "http://packages.dotdeb.org",
        release     => "squeeze",
        repos       => "all",
        include_src => "false",
        notify      => Exec["apt-get update"],
        require     => Apt::Key["dotdeb"]
    }

    # Until redis incorporated our custom changes, we have to use our own build.
    package{"aerofs-redis-server":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    service { "redis-server":
        ensure  => "running",
        provider   => 'upstart',
        enable  => "true",
        require => Package["aerofs-redis-server"],
    }

    # System-related config.
    line{ "sysctl.conf vm overcommit":
         ensure => present,
         file => "/etc/sysctl.conf",
         line => "vm.overcommit_memory = 1",
         require => Package["aerofs-redis-server"]
    }

    # Variables for inherited class templates.
    $redis_database_dir = "/data/redis"
    $redis_pidfile = "/run/redis.pid"
    $redis_logfile = "/var/log/redis/redis.log"
    $redis_port = 6379
    $redis_loglevel = "notice"
}
