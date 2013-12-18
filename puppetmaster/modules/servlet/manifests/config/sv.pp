#
# N.B. this is only used by public deployment.
#
class servlet::config::sv(
    $mysql_password,
    $mysql_endpoint
) {
    include servlet::sv
    include servlet::nginx_config

    # N.B. this is needed by context-footer.xml.erb
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

    servlet::config::file{"/etc/tomcat6/Catalina/localhost/sv_beta.xml":
        content => template(
            "servlet/context-header-sv.xml.erb",
            "servlet/context-footer.xml.erb"
        ),
        require => Package["aerofs-sv"]
    }

    servlet::log{"sv":
        config_filename => "/usr/share/aerofs-sv/sv/WEB-INF/classes/logback.xml",
        log_level       => "INFO",
        require         => Package["aerofs-sv"]
    }

}
