# == Class: redis-aof
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012-2013 Air Computing Inc, unless otherwise noted.
#
class redis::aof inherits redis {

    file{"/etc/redis/redis.conf":
        content => template(
            "redis/aof.conf.erb"
        ),
        require => Package["aerofs-redis-server"],
        notify  => Service["redis-server"]
    }
}
