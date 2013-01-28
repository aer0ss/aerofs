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
    line{ "redis.conf-aof-1":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "appendonly yes",
        require => Package["aerofs-redis-server"]
    }
    line{ "redis.conf-aof-2":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "appendfilename redis.aof",
        require => Package["aerofs-redis-server"]
    }
}
