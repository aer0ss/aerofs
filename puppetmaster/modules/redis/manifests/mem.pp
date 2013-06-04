class redis::mem inherits redis {

    file{"/etc/redis/redis.conf":
        content => template(
            "redis/mem.conf.erb"
        ),
        require => Package["aerofs-redis-server"]
    }
}
