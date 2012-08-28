node "sss.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    class {"servlet::mount":
        partition => "/dev/xvdf"
    }

    $mysql_syncstat = hiera("mysql_syncstat")
    $mysql_sp = hiera("mysql_sp")
    $mysql_endpoint = hiera("mysql_endpoint",undef)
    class{"servlet::syncstat":
        mysql_sp_username => $mysql_sp["username"],
        mysql_sp_password => $mysql_sp["password"],
        mysql_syncstat_username => $mysql_syncstat["username"],
        mysql_syncstat_password => $mysql_syncstat["password"],
        mysql_endpoint    => $mysql_endpoint,
    }
}
