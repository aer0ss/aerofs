IMAGE_NAME = aerofs/maintenance-nginx
RUN = buildroot/run-common.sh

image: $(RUN)
	$(MAKE) -f ../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

$(RUN): ../nginx/root/run-common.sh
	mkdir -p $(@D)
	cp $< $@

.PHONY: image
