class redis::aof inherits redis {

    file{"/etc/redis/redis.conf":
        content => template(
            "redis/aof.conf.erb"
        ),
        require => Package["aerofs-redis-server"]
    }
}
