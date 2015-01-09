IMAGE_NAME = aerofs/repackaging
ROOT = buildroot

# PREDOCKER
FILES = $(shell find -L opt -type f)

image: $(FILES:%=$(ROOT)/%) .copy-packages
	docker build -t $(IMAGE_NAME) .

$(ROOT)/%: %
	mkdir -p $(@D)
	cp $< $@

# Copy client packages
SRC_PKGS_DIR = ../../out.gradle/packages
DST_PKGS_DIR = $(ROOT)/opt/repackaging/installers/original

PKGS = $(shell ls $(SRC_PKGS_DIR))
DST_PKGS = $(PKGS:%=$(DST_PKGS_DIR)/%)

.copy-packages:
ifneq '$(words $(PKGS))' '15'
	$(error $(words $(PKGS)) files were found in $(SRC_PKGS_DIR). I expect 15)
endif
	../../tools/lazy-copy.sh $(SRC_PKGS_DIR) $(DST_PKGS_DIR)
