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

    file { "/etc/default/tomcat6":
      source => "puppet:///modules/tomcat6/tomcat6",
      owner   => "root",
      group   => "root",
      require => Package["tomcat6"],
      notify  => Service["tomcat6"],
    }

    service { "tomcat6":
        tag => ['autostart-overridable'],
        ensure     => running,
        enable     => true,
        hasstatus  => true,
        hasrestart => true,
    }
}
