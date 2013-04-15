class github-enterprise-tools {
    # For this file to be useful, the machine must:
    #   1) have an ssh public key on github.arrowfs.org
    #   2) have s3cmd installed and configured to use aerofs.github with encryption
    #
    # We do not automate that currently.
    file { "/usr/local/bin/backup_github":
        ensure => present,
        source => "puppet:///modules/github-enterprise-tools/backup_github",
        mode   => "744",
    }
}
