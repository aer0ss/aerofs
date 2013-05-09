class servlet::sp {
    package{"aerofs-sp":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    $databases = [
        {
            param_name => "sp_database_resource_reference",
            name => "SPDatabase"
        }
    ]

    # Because these config files use multiple templates, tests were written for their
    # content. See the RSpec tests for more details.
    servlet::config::file{"/usr/share/aerofs-sp/sp/WEB-INF/web.xml":
        content => template(
            "servlet/web-header.xml.erb",
            "servlet/web-common.xml.erb",
            "servlet/web-sp.xml.erb",
            "servlet/web-footer.xml.erb"
        ),
        require => Package["aerofs-sp"]
    }

    # Logging level.
    servlet::log{"/var/log/aerofs/sp.log":
        config_filename => "/usr/share/aerofs-sp/sp/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-sp"],
    }
}
