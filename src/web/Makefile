IMAGE_NAME = aerofs/web

# PREDOCKER
DST_DIR = buildroot/opt/web
FILES = $(shell find -L web -type f)
VERSION_FILE = buildroot/opt/repackaging/installers/original/current.ver

image: .make-source $(FILES:%=$(DST_DIR)/%) $(VERSION_FILE) $(DST_DIR)/production.ini.template
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

$(DST_DIR)/production.ini.template: ../../puppetmaster/modules/unified/files/production.ini.template
	mkdir -p $(@D)
	sed \
	    -e 's`use = egg:aerofs-web`use = call:web:main`' \
	    -e 's`virtualenv = /opt/web/pythonenv``' \
	    -e 's`localhost:5434`config.service:5434`' \
	    -e 's`localhost:8700`sparta.service:8700`' \
	    -e 's`localhost:8080/sp`sp.service:8080`' \
	    -e 's`localhost:8083`havre.service:8083`' \
	    $< > $@
