# == Class: redis-diskstore
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012-2013 Air Computing Inc, unless otherwise noted.
#
class redis::diskstore inherits redis {
    line{ "redis.conf-diskstore-1":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "diskstore-enabled yes",
        require => Package["aerofs-redis-server"]
    }
    line{ "redis.conf-diskstore-2":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "diskstore-path /var/log/redis/store",
        require => Package["aerofs-redis-server"]
    }
    line{ "redis.conf-diskstore-3":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "cache-flush-delay 5",
        require => Package["aerofs-redis-server"]
    }
    # TODO (MP) calculate as a percentage of the system memory. 4 gigs for now.
    line{ "redis.conf-diskstore-memory-4":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "cache-max-memory 4294967296",
        require => Package["aerofs-redis-server"]
    }

    delete_lines{ "redis.conf-diskstore-delete-1":
        file => "/etc/redis/redis.conf",
        pattern => "appendonly.*",
        require => Package["aerofs-redis-server"]
    }
    delete_lines{ "redis.conf-diskstore-delete-2":
        file => "/etc/redis/redis.conf",
        pattern => "appendfilename.*",
        require => Package["aerofs-redis-server"]
    }
    delete_lines{ "redis.conf-diskstore-delete-3":
        file => "/etc/redis/redis.conf",
        pattern => "save.*",
        require => Package["aerofs-redis-server"]
    }
    delete_lines{ "redis.conf-diskstore-delete-4":
        file => "/etc/redis/redis.conf",
        pattern => "dbfilename.*",
        require => Package["aerofs-redis-server"]
    }
}
