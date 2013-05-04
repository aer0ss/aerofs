class servlet::config::syncstat(
    $mysql_sp_password,
    $mysql_endpoint,
    $verkehr_host,
    $cacert_location
) {
    include servlet::syncstat
    $databases = [
        {
            param_name => "sp_database_resource_reference",
            name => "SPDatabase",
            user => "aerofs_sp_ro",
            password => $mysql_sp_password,
            endpoint => $mysql_endpoint,
            schema => "aerofs_sp",
        }
    ]

    # Because these config files use multiple templates, tests were written for their
    # content. See the RSpec tests for more details.

    servlet::config::file{"/etc/tomcat6/Catalina/localhost/ROOT.xml":
        content => template(
            "servlet/context-header-syncstat.xml.erb",
            "servlet/context-body.xml.erb",
            "servlet/context-footer.xml.erb"
        ),
        require => Package["aerofs-syncstat"]
    }

    servlet::config::file{"/usr/share/aerofs-syncstat/syncstat/WEB-INF/web.xml":
        content => template(
            "servlet/web-header.xml.erb",
            "servlet/web-common.xml.erb",
            "servlet/web-syncstat.xml.erb",
            "servlet/web-footer.xml.erb"
        ),
        require => Package["aerofs-syncstat"]
    }

    $config_filename = "/usr/share/aerofs-syncstat/syncstat/WEB-INF/classes/logback.xml"
    servlet::log{"/var/log/aerofs/syncstat.log":
        config_filename => $config_filename,
        log_level       => "WARN",
        require => Package["aerofs-syncstat"],
    }
}
