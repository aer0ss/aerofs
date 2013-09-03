class redis::aof (
    $redis_bindaddr = $redis::redis_bindaddr
) inherits redis {

    file{"/etc/redis/redis.conf":
        content => template(
            "redis/aof.conf.erb"
        ),
        require => Package["aerofs-redis-server"]
    }
}
