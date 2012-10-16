node "c.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    # Include the cmd service
    include cmd

    ### STAGING SYNCSTAT ####
    # Since we need a staging service, we need the staging cacert
    $cacert_location = "/etc/ssl/certs/AeroFS_CA_staging.pem"
    file{$cacert_location:
        source => "puppet:///aerofs_cacert/cacert-staging.pem",
        ensure => present,
    }

    # Fetch variables from hiera
    $mysql_sp = hiera("mysql_sp")
    $mysql_endpoint = hiera("mysql_endpoint")

    # install syncstat servlet
    class{"servlet::syncstat":
        mysql_sp_password       => $mysql_sp["password"],
        mysql_endpoint          => $mysql_endpoint,
        verkehr_host            => "staging.aerofs.com",
        cacert_location         => $cacert_location
    }
    ### END STAGING SYNCSTAT ###
}
