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
    line{ "redis.conf-in-mem-1":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "save 600 100",
        require => Package["aerofs-redis-server"]
    }
    line{ "redis.conf-in-mem-2":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "dbfilename redis.rdb",
        require => Package["aerofs-redis-server"]
    }
}
