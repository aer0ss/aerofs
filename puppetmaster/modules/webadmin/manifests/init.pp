# == Class: webadmin
#
# The webadmin module for aerofs
#
# === Parameters
#
# === Variables
#
# === Examples
#
#   include webadmin
#
# === Authors
#
# Peter Hamilton <peter@aerofs.com>
#
# === Copyright
#
# Copyright 2012 Air Computing Inc, unless otherwise noted.
#
class webadmin (
    $stripe_pub_key,
    $stripe_secret_key,
    $uwsgi_port = 8080
) {
    #
    # TODO puppet should configure nginx (the deb package currently does this).
    #
    $STRIPE_PUBLISHABLE_KEY = $stripe_pub_key
    $STRIPE_SECRET_KEY = $stripe_secret_key


    package{"aerofs-web":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    service{"uwsgi":
        ensure => running,
        require => [
            Package["aerofs-web"],
            File["/var/www"]
        ]
    }

    file{"/var/www":
        ensure => directory,
        owner  => "www-data",
    }

    file{"/etc/uwsgi/apps-enabled/productionAeroFS.ini":
        content => template("webadmin/productionAeroFS.ini.erb"),
        owner => root,
        group => root,
        require => Package["aerofs-web"],
        notify => Service["uwsgi"]
    }
}
