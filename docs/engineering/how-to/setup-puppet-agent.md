1. Create an instance on Amazon.
2. Boot the instance.
3. Change the hostname of the instance to be the same as puppet node. (For example, if your node is named "rocklog.aerofs.com", then name your host rocklog.aerofs.com.
4. Add `172.16.1.15 puppet` to /etc/hosts.
5. Add the following namespaces to /etc/resolv.conf

    ```
    nameserver 172.16.0.83
    search arrowfs.org
    nameserver 172.16.0.2
    ```

6. `apt-get update`.
7. `apt-get install puppet`.
8. `puppet agent -t --waitforcert puppet`.
9. Log into puppet master and sign the cert for the new box.

PROFIT!