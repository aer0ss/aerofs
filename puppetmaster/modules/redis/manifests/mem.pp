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
    # Delete saves of the wrong time interval.
    delete_lines{ "redis.conf-mem-1":
        file => "/etc/redis/redis.conf",
        pattern => "save.*",
        require => Package["aerofs-redis-server"]
    }
    # Use correct time interval.
    line{ "redis.conf-mem-2":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "save 600 100",
        require => Package["aerofs-redis-server"]
    }
    line{ "redis.conf-mem-3":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "dbfilename redis.rdb",
        require => Package["aerofs-redis-server"]
    }
    delete_lines{ "redis.conf-mem-4":
        file => "/etc/redis/redis.conf",
        pattern => "appendonly yes",
        require => Package["aerofs-redis-server"]
    }
    delete_lines{ "redis.conf-mem-5":
        file => "/etc/redis/redis.conf",
        pattern => "appendfilename redis.aof",
        require => Package["aerofs-redis-server"]
    }
}
