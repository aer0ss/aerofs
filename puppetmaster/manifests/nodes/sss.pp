node "sss.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    include redis::diskstore

    # Fetch variables from hiera
    $mysql_sp = hiera("mysql_sp")
    $mysql_endpoint = hiera("mysql_endpoint")

    # install syncstat servlet
    include servlet
    include nginx
    class{"servlet::nginx":
        proxy_read_timeout => "60",
        proxy_send_timeout => "60"
    }
    class{"servlet::config::syncstat":
        mysql_sp_password       => $mysql_sp["password"],
        mysql_endpoint          => $mysql_endpoint,
        verkehr_host            => "verkehr.aerofs.com",
        cacert_location         => "/etc/ssl/certs/AeroFS_CA.pem"
    }
}
