FROM jwilder/docker-gen:0.4.3
MAINTAINER AeroFS "support@aerofs.com"

ADD root/nginx.tmpl /

ENTRYPOINT ["/usr/local/bin/docker-gen", \
            "-watch", \
            "-only-exposed", \
            "-notify-sighup", "hpc-reverse-proxy", \
            "/nginx.tmpl", \
            "/etc/nginx/conf.d/nginx-generated.conf"]
