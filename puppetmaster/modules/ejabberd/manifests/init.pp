class ejabberd(
    $mysql_password
){
    package { "ejabberd":
        ensure => installed
    }

    service { "ejabberd":
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

    exec { "mysql -D ${mysql_db} -h ${mysql_host} -p${mysql_password} -u ${mysql_user}< /etc/ejabberd/mysql.sql":
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

    firewall { "500 forward traffic for xmpp clients on port 443":
        table   => "nat",
        chain   => "PREROUTING",
        iniface => "eth0",
        dport   => "443",
        jump    => "REDIRECT",
        toports => "5222"
    }
}
