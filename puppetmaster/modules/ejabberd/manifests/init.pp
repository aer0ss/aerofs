class ejabberd (
    $mysql_password,
    $port = 5222
) {
    package { "ejabberd":
        ensure => "2.1.10-2ubuntu1"
    }

    service { "ejabberd":
        tag => ['autostart-overridable'],
        ensure    => running,
        hasstatus => false,
        pattern   => "beam",
    }

    include mysql::server

    $mysql_user     = "ejabberd"
    $mysql_host     = "localhost"
    $mysql_db       = "ejabberd"
    mysql::db { $mysql_db:
        user     => $mysql_user,
        password => $mysql_password,
        host     => $mysql_host,
        grant    => ['all']
    }

    file { "/etc/ejabberd/mysql.sql":
        source  => "puppet:///modules/ejabberd/mysql.sql",
        owner   => root,
        group   => root,
        mode    => 666,
        require => [
            Package["ejabberd"],
            Mysql::Db[$mysql_db],
        ]
    }

    exec { "/usr/bin/mysql -D ${mysql_db} -h ${mysql_host} -p${mysql_password} -u ${mysql_user}< /etc/ejabberd/mysql.sql":
        subscribe   => File["/etc/ejabberd/mysql.sql"],
        refreshonly => true
    }

    file { "/etc/ejabberd/ejabberd.cfg":
        content => template("ejabberd/ejabberd.cfg.erb"),
        owner   => "ejabberd",
        group   => "ejabberd",
        require => Package["ejabberd"],
        notify  => Service["ejabberd"],
    }

    file { "/etc/ejabberd/auth_all":
        source => "puppet:///modules/ejabberd/auth_all",
        mode   => "755",
        require => Package["ejabberd"],
        notify => Service["ejabberd"]
    }

    file { "/etc/init.d/ejabberd":
        source => "puppet:///modules/ejabberd/ejabberd",
        mode   => "755",
        owner  => "root",
        group  => "root",
        notify => Service["ejabberd"]
    }

    file { "/etc/default/ejabberd":
        source => "puppet:///modules/ejabberd/default_ejabberd",
        mode   => "755",
        owner  => "root",
        group  => "root",
        require=> Package["ejabberd"],
        notify => Service["ejabberd"]
    }

    file { "/usr/lib/ejabberd/ebin":
        source  => "puppet:///modules/ejabberd/ebin",
        notify  => Service["ejabberd"],
        recurse => true
    }

    $ejabberd_check = "/etc/ejabberd/ejabberd_check"
    file { $ejabberd_check:
        source => "puppet:///modules/ejabberd/ejabberd_check",
        mode   => "755",
        owner  => "root",
        group  => "root",
        require => Package["ejabberd"]
    }

    cron { "ejabberd_check":
        command => "${ejabberd_check}",
        user => "root",
        minute => "*/3"
    }
}
