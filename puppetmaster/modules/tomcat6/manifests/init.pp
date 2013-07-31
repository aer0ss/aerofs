class tomcat6 {
    package { "tomcat6":
       ensure => latest,
    }

    package { "tomcat6-admin":
        ensure  => latest,
        require => Package["tomcat6"],
    }

    package { "libmysql-java":
        ensure => latest,
    }

    service { "tomcat6":
        tag => ['autostart-overridable'],
        ensure     => running,
        enable     => true,
        hasstatus  => true,
        hasrestart => true,
    }
}
