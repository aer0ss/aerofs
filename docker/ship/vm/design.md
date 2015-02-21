
# Host-Loader interface
 
Specify one of the following Loader commands when launching the loader. For example:
`docker run <loader_image> verify`:

- `verify` checks validity of the two files from inherited images: `crane.yml` and `tag`,
- and exits non-zero if the files don't exist or their contents are invalid.
 
- `images` prints all the images used by the application as specified in /crane.yml.

- `tag` prints the value of /tag.

- `load` launches all the containers of the app, waits until the app needs a restart,
  and exits. This command requires the host's docker.sock:

      docker run -v /var/run/docker.sock:/var/run/docker.sock
          -v <host_repo_file>:<repo_file> -v <host_target_file>:<target_file>
          <loader_image> load <repo_file> <target_file> [tag]

   Optionally specify a tag as the parameter to override the value specified in /tag.
   This option is for testing only.

   Important: `load` assumes that the host didn't alter the Loader container's hostname
   when launching it (via `docker run -h`). See loader.py:my_container_id().
   
- `install-getty <path>` copies executable files to run the console service to the
folder specified by "path". The host system will then execute the `run` file in this
folder on the first virtual terminal (tty1).

- `test-getty` runs the console service in the container, used mainly to test the banner.
This command may need '-it' docker options to work properly.

- `modified-yaml` prints the modified yaml for actual loading of the app. For testing only.
It requires mounting of the docker.sock file.

# VM build process

[Reference](https://github.com/coreos/docs/blob/a18d3605f85d20df552696e90903097a
4de01650/sdk-distributors/distributors/notes-for-distributors/index.md). Note: 
the instructions to write the OEM partition have been deleted from the latest 
version of this doc.

In the long run, cloud-config files can be the only thing we manually deliver 
to the customers who don't mind using the public registry hosted by us. The way 
the customer initializes the box will be exactly the same as the above minus 
the first and last step.

# Tips and Tricks

Run cloud-init manually:

    $ sudo coreos-cloudinit --from-file /usr/share/oem/cloud-config.yml
