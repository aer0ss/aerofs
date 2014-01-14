class redis {
    # Until redis incorporated our custom changes, we have to use our own build.
    package{"aerofs-redis-server":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    # Ensure the default redis configuration file is absent (since we create our
    # own custom conf files).
    file {"/etc/redis/redis.conf" :
        ensure => absent,
        require => Package["aerofs-redis-server"]
    }

    # Ditto wrt upstart related files.
    file {"/etc/init/redis-server.conf" :
        ensure => absent,
        require => Package["aerofs-redis-server"]
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
    $redis_loglevel = "notice"
    $redis_bindaddr = "all"
}
