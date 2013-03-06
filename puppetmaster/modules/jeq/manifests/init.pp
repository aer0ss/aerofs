# == Class: jeq
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012-2013 Air Computing Inc, unless otherwise noted.
#
class jeq {
    package{"aerofs-jeq-tools":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }
}
