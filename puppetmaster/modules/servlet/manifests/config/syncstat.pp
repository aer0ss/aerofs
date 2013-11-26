#
# N.B. This is only used by public deployment.
#
class servlet::config::syncstat(
    $mysql_sp_password,
    $mysql_endpoint,
    $verkehr_host,
    $cacert_location
) {
    include servlet::syncstat

    # N.B. this is needed by context-footer.xml.erb
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

    servlet::config::file{"/etc/tomcat6/Catalina/localhost/ROOT.xml":
        content => template(
            "servlet/context-header-syncstat.xml.erb",
            "servlet/context-footer.xml.erb"
        ),
        require => Package["aerofs-syncstat"]
    }

    $config_filename = "/usr/share/aerofs-syncstat/syncstat/WEB-INF/classes/logback.xml"
    servlet::log{"syncstat":
        config_filename => $config_filename,
        log_level       => "WARN",
        require => Package["aerofs-syncstat"],
    }
}
