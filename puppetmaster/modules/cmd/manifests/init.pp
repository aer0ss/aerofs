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
#   include cmd
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

    include redis

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

    # Ensure redis is in journalling mode for the command server.
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

    include nginx

    nginx::resource::vhost {"cmd-vhost":
        listen_port          => '80',
        proxy                => 'http://127.0.0.1:9080',
        ensure               => present,
    }
}
