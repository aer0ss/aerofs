class public-zelda-creds {
    file {"/etc/aerofs":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "644",
    }
    file {"/etc/aerofs/zelda.properties":
        require => File["/etc/aerofs"],
        source => "puppet:///modules/public-zelda-creds/zelda.properties",
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "644",
        notify => Service["tomcat6"],
    }
}
