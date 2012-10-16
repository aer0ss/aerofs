# == Class: redis
#
# === Parameters
#
# === Variables
#
#
# === Examples
#
#   include redis
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class redis {

    apt::key { "dotdeb":
        ensure => present,
        key_source => "http://www.dotdeb.org/dotdeb.gpg"
    }

    apt::source { "dotdeb":
        location    => "http://packages.dotdeb.org",
        release     => "squeeze",
        repos       => "all",
        include_src => "false",
        notify      => Exec["apt-get update"],
        require     => Apt::Key["dotdeb"]
    }

    # Until redis 2.6 is rolled out publicly, we have to use our own build.
    package{"aerofs-redis-server":
        ensure => installed,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    line{ "redis.conf1":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "port 6379",
        require => Package["aerofs-redis-server"]
    }

    line{ "redis.conf2":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "daemonize yes",
        require => Package["aerofs-redis-server"]
    }

    line{ "redis.conf3":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "pidfile /tmp/redis.pid",
        require => Package["aerofs-redis-server"]
    }

    line{ "redis.conf4":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "save 60 100",
        require => Package["aerofs-redis-server"]
    }

    line{ "redis.conf5":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "logfile /var/log/redis/redis.log",
        require => Package["aerofs-redis-server"]
    }

    line{ "redis.conf6":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "dbfilename /var/log/redis/redis.rdb",
        require => Package["aerofs-redis-server"]
    }

    line{ "redis.conf7":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "loglevel debug",
        require => Package["aerofs-redis-server"]
    }

    line{ "sysctl.conf vm overcommit":
         ensure => present,
         file => "/etc/sysctl.conf",
         line => "vm.overcommit_memory = 1",
         require => Package["aerofs-redis-server"]
    }
}
