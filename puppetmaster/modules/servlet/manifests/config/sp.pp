class servlet::config::sp(
    $mysql_password,
    $mysql_endpoint
) {
    include servlet::sp
    $databases = [
        {
            name => "SPDatabase",
            user => "aerofs_sp",
            password => $mysql_password,
            endpoint => $mysql_endpoint,
            schema => "aerofs_sp",
        }
    ]

    servlet::config::file{"/etc/tomcat6/Catalina/localhost/ROOT.xml":
        content => template(
            "servlet/context-header-sp.xml.erb",
            "servlet/context-body.xml.erb",
            "servlet/context-footer.xml.erb"
        ),
        require => Package["aerofs-sp"]
    }
}
