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

    # Until redis is rolled out publicly, we have to use our own build.
    package{[
        "aerofs-cmd-server",
        "aerofs-cmd-tools",
        "python-redis"
    ]:
        ensure => latest,
        require => [
            Package["aerofs-redis-server"],
            Apt::Source["aerofs"]
        ]
    }

    include nginx

    nginx::resource::vhost {"cmd-vhost":
        listen_port          => '80',
        proxy                => 'http://127.0.0.1:9080',
        ensure               => present,
    }
}
