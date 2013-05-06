class private-common {

    # This is our production apt key. OpenStack images should only be built off
    # production (for now).
    $aptkey = '64E72541'

    class{"common":
        aptkey => $aptkey,
        repo => "production"
    }

    include bootstrap
}
