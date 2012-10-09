class servlet::config::sv(
    $mysql_password,
    $mysql_endpoint
) {
    $databases = [
        {
            param_name => "sv_database_resource_reference",
            name => "SVDatabase",
            user => "aerofs_sv",
            password => $mysql_password,
            endpoint => $mysql_endpoint,
            schema => "aerofs_sv",
        }
    ]

    # Because these config files use multiple templates, tests were written for their
    # content. See the RSpec tests for more details.

    servlet::config::file{"/usr/share/aerofs-sv/sv/WEB-INF/web.xml":
        content => template(
            "servlet/web-header.xml.erb",
            "servlet/web-common.xml.erb",
            "servlet/web-sv.xml.erb",
            "servlet/web-footer.xml.erb"
        ),
        require => Package["aerofs-sv"]
    }

    servlet::config::file{"/etc/tomcat6/Catalina/localhost/sv_beta.xml":
        content => template(
            "servlet/context-header-sv.xml.erb",
            "servlet/context-body.xml.erb",
            "servlet/context-footer.xml.erb"
        ),
        require => Package["aerofs-sv"]
    }

    servlet::log{"/var/log/aerofs/sv.log":
        config_filename => "/usr/share/aerofs-sv/sv/WEB-INF/classes/log4j.properties",
        require         => Package["aerofs-sv"]
    }

}
