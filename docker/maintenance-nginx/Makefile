IMAGE_NAME = aerofs/maintenance-nginx

FILES = run-common.sh \
		etc/nginx/certs/dhparam.pem \
		etc/nginx/ssl-and-headers.conf \
		etc/nginx/sites/config \
		etc/nginx/sites/bunker

image: buildroot
	../nginx/build.sh
	docker build -t $(IMAGE_NAME) .

buildroot:
	rm -rf buildroot/
	for f in $(FILES) ; do \
		mkdir -p buildroot/$$(dirname $$f) ; \
		cp -a ../nginx/root/$$f buildroot/$$f ;\
	done

.PHONY: image buildroot
