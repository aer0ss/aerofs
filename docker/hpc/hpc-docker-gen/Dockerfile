FROM jwilder/docker-gen:0.4.3

MAINTAINER AeroFS "support@aerofs.com"

ENTRYPOINT ["/usr/local/bin/docker-gen", \
            "-watch", \
            "-only-exposed", \
            "-notify-sighup", "hpc-reverse-proxy", \
            "/nginx.tmpl", \
            "/etc/nginx/conf.d/nginx-generated.conf"]

ADD root/nginx.tmpl /
