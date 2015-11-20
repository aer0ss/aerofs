IMAGE_NAME = aerofs/repackaging
ROOT = buildroot

image: .copy-packages
	docker build -t $(IMAGE_NAME) .

# Copy client packages
SRC_PKGS_DIR = ../../out.gradle/packages
DST_PKGS_DIR = $(ROOT)/opt/repackaging/installers/original

PKGS = $(shell ls $(SRC_PKGS_DIR))
DST_PKGS = $(PKGS:%=$(DST_PKGS_DIR)/%)

.copy-packages:
ifeq (,$(filter 17 18, $(words $(PKGS))))
	$(error $(words $(PKGS)) files were found in $(SRC_PKGS_DIR). I expect 17 or 18(syncdet only))
endif
	# Remove old files from buildroot. TODO (WW) use the buildroot-syncroot approach
	rm -rf $(DST_PKGS_DIR)/*
	../../tools/lazy-copy.sh $(SRC_PKGS_DIR) $(DST_PKGS_DIR)

.PHONY: image .copy-packages
