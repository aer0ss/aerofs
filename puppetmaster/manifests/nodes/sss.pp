node "sss.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    include redis

    # Fetch variables from hiera
    $mysql_syncstat = hiera("mysql_syncstat")
    $mysql_sp = hiera("mysql_sp")
    $mysql_endpoint = hiera("mysql_endpoint")

    # install syncstat servlet
    class{"servlet::syncstat":
        mysql_sp_password       => $mysql_sp["password"],
        mysql_syncstat_password => $mysql_syncstat["password"],
        mysql_endpoint          => $mysql_endpoint,
        verkehr_host            => "verkehr.aerofs.com",
        cacert_location         => "/etc/ssl/certs/AeroFS_CA.pem"
    }
}
