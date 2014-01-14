class redis::aof (
    $redis_bindaddr = $redis::redis_bindaddr
) inherits redis {

    # Variables for inherited class templates.
    $redis_port = 6379
    $redis_conf_filename = "/etc/redis/redis-aof.conf"
    $redis_pidfile = "/run/redis-aof.pid"
    $redis_logfile = "/var/log/redis/redis-aof.log"

    file{"/etc/redis/redis-aof.conf":
        content => template(
            "redis/aof.conf.erb"
        ),
        require => Package["aerofs-redis-server"]
    }

    service { "redis-aof":
        ensure  => running,
        provider => upstart,
        enable  => true,
        require => File["/etc/init.d/redis-aof"],
    }
    file{"/etc/init.d/redis-aof":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => File["/etc/init/redis-aof.conf"],
    }
    file{"/etc/init/redis-aof.conf":
        content => template(
            "redis/redis.conf.erb"
        ),
        require => Package["aerofs-redis-server"]
    }
}
