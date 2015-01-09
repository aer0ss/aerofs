This folder includes scripts commonly used by apps in containers. Usage:

    $(MAKE) -f <path_to_this_dir>/Makefile

This will copy all the container scripts to $PWD/buildroot/container-scripts. 
Thus,
the scripts will be accessible at /container-scripts in your to-be-built 
containers.
