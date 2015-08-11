# Use buildroot/syncroot build technique to resolve soft links for shared
# nginx resources.

IMAGE_NAME = aerofs/maintenance-nginx
RUN = buildroot/run-common.sh

FILES_HOME = root
FILES = $(shell find -L $(FILES_HOME) -type f)

BUILD_ROOT = buildroot
SYNC_ROOT = syncroot
SYNC_DIR = syncroot

image: $(BUILD_ROOT) $(RUN)
	$(MAKE) -f ../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

$(RUN): ../nginx/root/run-common.sh
	mkdir -p $(@D)
	cp $< $@

$(BUILD_ROOT): $(SYNC_ROOT) $(FILES:%=$(SYNC_DIR)/%)
	mv $(SYNC_ROOT)/$(FILES_HOME)/* $(SYNC_ROOT)
	rsync --archive --checksum --no-times --delete $</ $@
	-rm -rf $<

$(SYNC_ROOT):
	-rm -rf $@
	mkdir -p $@

$(SYNC_DIR)/%: %
	mkdir -p $(@D)
	cp $< $@

.PHONY: image $(SYNC_ROOT)
