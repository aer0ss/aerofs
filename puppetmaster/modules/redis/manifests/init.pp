# == Class: redis
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012-2013 Air Computing Inc, unless otherwise noted.
#
class redis {

    define delete_lines($file, $pattern) {
        exec { "/bin/sed -i -r -e '/$pattern/d' $file":
            onlyif => "/bin/grep -E '$pattern' '$file'",
        }
    }

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
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    # Port information.
    line{ "redis.conf1":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "port 6379",
        require => Package["aerofs-redis-server"]
    }

    # PID file.
    line{ "redis.conf2":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "pidfile /run/redis.pid",
        require => Package["aerofs-redis-server"]
    }

    # Logging.
    line{ "redis.conf3":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "logfile /var/log/redis/redis.log",
        require => Package["aerofs-redis-server"]
    }
    line{ "redis.conf4":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "loglevel debug",
        require => Package["aerofs-redis-server"]
    }

    # Database directory.
    line{ "redis.conf5":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "dir /var/log/redis",
        require => Package["aerofs-redis-server"]
    }

    # System-related config.
    line{ "sysctl.conf vm overcommit":
         ensure => present,
         file => "/etc/sysctl.conf",
         line => "vm.overcommit_memory = 1",
         require => Package["aerofs-redis-server"]
    }
}
