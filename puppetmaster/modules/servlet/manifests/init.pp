# == Class: servlet
#
# Servlet contains all the functionality to run tomcat6 servlets. Each
# individual application has it's own manifest. (ex. servlet::syncstat).
#
# N.B. This class is only used by the public deployment.
#
class servlet {
    include nginx
    include servlet::nginx_config

    $metrics = hiera("metrics")
    class{"servlet::base":
        tomcat6_user => hiera("tomcat6_manager")
    }
}
