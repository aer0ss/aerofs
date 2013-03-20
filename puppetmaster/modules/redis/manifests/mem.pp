# == Class: redis-mem
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012-2013 Air Computing Inc, unless otherwise noted.
#
class redis::mem inherits redis {

    servlet::config::file{"/etc/redis/redis.conf":
        content => template(
            "redis/mem.conf.erb"
        ),
        require => Package["aerofs-redis-server"],
        notify  => Service["redis-server"]
    }
}
