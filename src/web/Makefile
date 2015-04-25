IMAGE_NAME = aerofs/web

# PREDOCKER
DST_DIR = buildroot/opt/web
FILES = $(shell find -L web -type f)
VERSION_FILE = buildroot/opt/repackaging/installers/original/current.ver

image: .make-source $(FILES:%=$(DST_DIR)/%) $(VERSION_FILE)
	docker build -t $(IMAGE_NAME) .

.PHONY: image .make-source

.make-source:
	$(MAKE) -C web

$(DST_DIR)/%: %
	mkdir -p $(@D)
	cp $< $@

$(DST_DIR)/web/views/devices/devices_view.py: web/views/devices/devices_view.py devices_view.py.docker.patch
	mkdir -p $(@D)
	cp $< $@
	patch $@ < devices_view.py.docker.patch

$(DST_DIR)/web/util.py: web/util.py
	mkdir -p $(@D)
	sed -e "s/'base.sp.url'/'deployment.sp_server_uri'/" $< > $@

$(DST_DIR)/web/login_util.py: web/login_util.py
	mkdir -p $(@D)
	sed -e "s/'base.sp.url'/'deployment.sp_server_uri'/" $< > $@

$(DST_DIR)/web/views/login/openid.py: web/views/login/openid.py
	mkdir -p $(@D)
	sed -e "s/'base.sp.url'/'deployment.sp_server_uri'/" $< > $@

$(VERSION_FILE): ../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	cp $< $@
