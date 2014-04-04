Use this vagrant folder to create a Linux vagrant box. Use this box to build Linux-specific packages for production systems.

For the time being the only package this box can build is nginx. We hope to add support for more packages in the future.

Usage
---

    $ cd {this folder}
    $ vagrant up
    $ vagrant ssh
    
And then from within the ssh shell:

    $ sudo /vagrant/build_nginx.sh

Once built, a .deb package will be avalable in this folder.

Then, upload the .deb to apt.aerofs.com, and manually `reprepro` it into everyone's repo, including _default_.