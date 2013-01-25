class servlet::config::sp(
    $mysql_password,
    $mysql_endpoint
) {
    $databases = [
        {
            param_name => "sp_database_resource_reference",
            name => "SPDatabase",
            user => "aerofs_sp",
            password => $mysql_password,
            endpoint => $mysql_endpoint,
            schema => "aerofs_sp",
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

    servlet::config::file{"/etc/tomcat6/Catalina/localhost/ROOT.xml":
        content => template(
            "servlet/context-header-sp.xml.erb",
            "servlet/context-body.xml.erb",
            "servlet/context-footer.xml.erb"
        ),
        require => Package["aerofs-sp"]
    }

    servlet::log{"/var/log/aerofs/sp.log":
        config_filename => "/usr/share/aerofs-sp/sp/WEB-INF/classes/log4j.properties",
        log_level       => "INFO",
        require         => Package["aerofs-sp"],
    }
}
