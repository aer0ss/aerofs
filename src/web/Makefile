IMAGE_NAME = aerofs/web

BUILD_ROOT = buildroot
SYNC_ROOT = syncroot
SYNC_DIR = syncroot/opt/web
FILES = $(shell find -L web -type f)
VERSION_FILE = syncroot/opt/repackaging/installers/original/current.ver

image: .make-source $(BUILD_ROOT)
	docker build -t $(IMAGE_NAME) .

.PHONY: image .make-source $(SYNC_ROOT)

.make-source:
	$(MAKE) -C web

$(BUILD_ROOT): $(SYNC_ROOT) $(FILES:%=$(SYNC_DIR)/%) $(VERSION_FILE)
	rsync --archive --checksum --no-times --delete $</ $@
	-rm -rf $<

# TODO (WW) move everything from 'web' to 'root' folder
$(SYNC_ROOT):
	-rm -rf $@
	mkdir -p $@

$(SYNC_DIR)/%: %
	mkdir -p $(@D)
	cp $< $@

$(VERSION_FILE): ../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	cp $< $@
