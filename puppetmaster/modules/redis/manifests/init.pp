class redis {
    # Until redis incorporated our custom changes, we have to use our own build.
    package{"aerofs-redis-server":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    file{"/etc/init.d/redis-server":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-redis-server"],
    }

    service { "redis-server":
        ensure  => running,
        provider => upstart,
        enable  => true,
        require => File["/etc/init.d/redis-server"],
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
    $redis_bindaddr = "all"
}
