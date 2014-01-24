# AeroFS Service
#
# Defines an AeroFS service that is:
# - Has an upstart init script
# - Is managed by upstart
# - Has a logrotate configuration
define common::service (
    $service_name = $title
) {
    package{"aerofs-${service_name}":
        ensure => latest,
        require => Apt::Source["aerofs"],
    }

    file{"/etc/init.d/${service_name}":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-${service_name}"],
    }

    logrotate::log{"${service_name}":
        # intentionally left blank
    }

    service{"${service_name}":
        tag => ['autostart-overridable'],
        enable => true,
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/${service_name}"],
    }
}
