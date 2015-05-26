IMAGE_NAME = aerofs/web

DST_DIR = buildroot/opt/web
FILES = $(shell find -L web -type f)
VERSION_FILE = buildroot/opt/repackaging/installers/original/current.ver

image: .make-source $(FILES:%=$(DST_DIR)/%) $(VERSION_FILE)
	docker build -t $(IMAGE_NAME) .

.PHONY: image .make-source

.make-source:
	$(MAKE) -C web

# TODO (WW) move everything from 'web' to 'root' folder
$(DST_DIR)/%: %
	mkdir -p $(@D)
	cp $< $@

$(VERSION_FILE): ../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	cp $< $@
