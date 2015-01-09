IMAGE_NAME = aerofs/bunker
ROOT = buildroot/opt/bunker

FILES = $(shell find -L requirements-exact.txt entry.py web -type f)
VERSION_FILE = buildroot/opt/repackaging/installers/original/current.ver
PRODUCTION_INI = $(ROOT)/production.ini

image: .make-web $(FILES:%=$(ROOT)/%) $(VERSION_FILE) $(PRODUCTION_INI)
	docker build -t $(IMAGE_NAME) .

# make sure all the web assets that we'll be using exist
.make-web:
	$(MAKE) -C ../web/web

$(ROOT)/%: %
	mkdir -p $(@D)
	cp $< $@ 

# PREDOCKER
$(ROOT)/web/views/maintenance/templates/setup/apply_page.mako: web/views/maintenance/templates/setup/apply_page.mako
	mkdir -p $(@D)
	cp $< $@ 
	# sed has no fun for this file. use patch instead.
	patch $@ < apply_page.docker.patch

$(ROOT)/web/views/maintenance/__init__.py: web/views/maintenance/__init__.py
	mkdir -p $(@D)
	cp $< $@ 
	patch $@ < maintenance_init.docker.patch

$(ROOT)/web/views/maintenance/setup_view.py: web/views/maintenance/setup_view.py
	mkdir -p $(@D)
	sed -e "s/host = 'localhost'/host = 'postfix.service'/" $< > $@

$(ROOT)/web/views/maintenance/templates/setup/email_page.mako: web/views/maintenance/templates/setup/email_page.mako
	mkdir -p $(@D)
	sed -e 's/public_host_name != "localhost"/public_host_name != "postfix.service"/' $< > $@

$(ROOT)/web/__init__.py: web/__init__.py
	mkdir -p $(@D)
	sed -e 's`/opt/bunker/state`/data2/bunker`' $< > $@

$(VERSION_FILE): ../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	cp $< $@

$(PRODUCTION_INI): ../../puppetmaster/modules/bunker/files/production.ini
	sed \
		-e 's`localhost:5434`config.service:5434`' \
		-e 's`localhost:8080/verification`verification.service:8080`' \
		-e 's`localhost:8080/log-collection`logcollection.service:8080`' \
		-e 's`localhost:8000`status.service:8000`' \
		-e 's`localhost:8700`bifrost.service:8700`' \
		-e 's`/opt/bunker/state`/data2/bunker`' \
		$< > $@
