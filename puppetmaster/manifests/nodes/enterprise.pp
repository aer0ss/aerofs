node "enterprise.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    # Include license website
    class { "lizard":
        mysql_password => hiera("mysql_password"),
        stripe_publishable_key => hiera("STRIPE_PUBLISHABLE_KEY"),
        stripe_secret_key => hiera("STRIPE_SECRET_KEY"),
        hpc_aws_access_key => hiera("HPC_AWS_ACCESS_KEY"),
        hpc_aws_secret_key => hiera("HPC_AWS_SECRET_KEY"),
    }
    # Include nginx configurations
    include lizard::nginx


    # Include mysql (for mysql client)
    # FIXME dep lost. Comment out for now.
    #include mysql
}
