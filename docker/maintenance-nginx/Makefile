include ../Makefile.base

IMAGE_NAME = aerofs/maintenance-nginx

FILES = run-common.sh \
		etc/nginx/certs/dhparam.pem \
		etc/nginx/ssl-and-headers.conf \
		etc/nginx/sites/config \
		etc/nginx/sites/bunker

buildroot: clean
	for f in $(FILES) ; do \
		mkdir -p buildroot/$$(dirname $$f) ; \
		cp -a ../nginx/root/$$f buildroot/$$f ;\
	done

build:
	docker build -t aerofs/nginx -f ../base/base/Dockerfile.nginx ../base/base
