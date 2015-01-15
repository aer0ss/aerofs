IMAGE_NAME = aerofs/maintenance-nginx
RUN = buildroot/get-browser-cert-and-run-nginx.sh

image: $(RUN)
	$(MAKE) -f ../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

$(RUN): ../nginx/root/get-browser-cert-and-run-nginx.sh
	mkdir -p $(@D)
	cp $< $@
