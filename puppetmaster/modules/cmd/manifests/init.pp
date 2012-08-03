# == Class: cmd
#
# Full description of class cmd here.
#
# === Parameters
#
# === Variables
#
#
# === Examples
#
#  class { cmd:
#  }
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class cmd {

    apt::source { "dotdeb":
        location    => "https://packages.dotdeb.org",
        repos       => "all",
        include_src => "false",
        key         => "89DF5277",
        key_server  => "keys.gnupg.net",
    }

    package{[
        "aerofs-cmd-server",
        "aerofs-cmd-tools",
    ]:
        ensure => latest,
        require => [
            Package["redis-server"],
            Apt::Source["aerofs"]
        ]
    }

    package{"redis-server":
        ensure => installed,
        require => [
            Apt::Source["dotdeb"]
        ]
    }

    line{ "redis.conf1":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "appendonly yes",
        require => Package["redis-server"]
    }

    line{ "redis.conf2":
        ensure => present,
        file => "/etc/redis/redis.conf",
        line => "appendfilename /var/log/redis/redis.aof",
        require => Package["redis-server"]
    }

    line{ "sysctl.conf vm overcommit":
        ensure => present,
        file => "/etc/sysctl.conf",
        line => "vm.overcommit_memory = 1",
        require => Package["redis-server"]
    }
}
