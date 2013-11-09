class public-email-creds {
    file {"/etc/aerofs":
        ensure => directory,
        owner  => "root",
        group  => "root",
        mode   => "644",
    }
    file {"/etc/aerofs/mail.properties":
        require => File["/etc/aerofs"],
        source => "puppet:///modules/public-email-creds/mail.properties",
        ensure => present,
        owner  => "root",
        group  => "root",
        mode   => "644",
        notify => Service["tomcat6"],
    }
}
