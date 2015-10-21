node "c.aerofs.com" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    include cmd
    include dbtools
    include github-enterprise-tools

    cron {"Backup github":
        command => "/usr/local/bin/backup_github",
        user => root,
        hour => 10,
        minute => 0,
    }
}
