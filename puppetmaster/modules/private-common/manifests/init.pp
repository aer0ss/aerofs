class private-common {
    # TODO: in puppet >2.7, this can be file() instead of template()
    $aptkey = template('common/aerofs-apt-key')

    # If there's a repo defined in hiera use that, otherwise use prod by default
    class{"common":
        aptkey => $aptkey,
        repo => hiera("environment","")
    }

    # Always run apt-get update before installing any packages
    # see: http://stackoverflow.com/questions/10845864/puppet-trick-run-apt-get-update-before-installing-other-packages
    exec {"apt-get-update":
        command => "/usr/bin/apt-get update"
    }
    Exec["apt-get-update"] -> Package <| |>

    # Now, having set a few default values & relationships, it's time to start working...
    include bootstrap
    include sanity
}
