"""Manages the remote AeroFS client processes and their installations.

This module perfoms the following management functions:

- starts and stops the AeroFS client
- backs-up the installation directories (app root and rt root)
- rolls back the installation directories
- modifies a version file in the installation directory to trick AeroFS
  into thinking it is running a different version

"""