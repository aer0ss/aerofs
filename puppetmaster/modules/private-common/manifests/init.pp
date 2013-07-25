class private-common {
    # This is our production apt key. OpenStack images should only be built off
    # production (for now).
    $aptkey = '64E72541'

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
