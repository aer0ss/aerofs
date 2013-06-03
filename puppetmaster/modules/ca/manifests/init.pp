# == Class: ca
#
# Full description of class ca here.
#
# === Parameters
#
# === Variables
#
#
# === Examples
#
#   include ca
#
# === Authors
#
# Matt Pillar <matt@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class ca {
    package{[
        "aerofs-ca-server",
        "aerofs-ca-tools",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    package { [
        "screen"
        ]:
        ensure => latest,
    }
}
