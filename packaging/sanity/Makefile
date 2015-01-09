IMAGE_NAME = aerofs/status
ROOT = buildroot

FILES = $(shell find -L opt -type f)

DST_PROBES_DIR = $(ROOT)/opt/sanity/probes
PROBES = ca mysql redis bifrost charlie ejabberd havre polaris sparta verkehr zephyr
PROBES := $(PROBES:%=$(DST_PROBES_DIR)/%.sh)

image: $(FILES:%=$(ROOT)/%) $(PROBES)
	docker build -t $(IMAGE_NAME) .

$(ROOT)/%: %
	mkdir -p $(@D)
	cp $< $@

$(DST_PROBES_DIR)/%.sh:
	mkdir -p $(@D)
	sed -e 's`localhost`$*.service`' $< > $@
	chmod a+x $@

PERSISTENT_DIR = ../../puppetmaster/modules/persistent/files/probes
TRANSIENT_DIR = ../../puppetmaster/modules/transient/files/probes

$(DST_PROBES_DIR)/ca.sh: $(PERSISTENT_DIR)/ca.sh
$(DST_PROBES_DIR)/mysql.sh: $(PERSISTENT_DIR)/mysql.sh
$(DST_PROBES_DIR)/redis.sh: $(PERSISTENT_DIR)/redis.sh
$(DST_PROBES_DIR)/bifrost.sh: $(TRANSIENT_DIR)/bifrost.sh
$(DST_PROBES_DIR)/charlie.sh: $(TRANSIENT_DIR)/charlie.sh
$(DST_PROBES_DIR)/ejabberd.sh: $(TRANSIENT_DIR)/ejabberd.sh
$(DST_PROBES_DIR)/havre.sh: $(TRANSIENT_DIR)/havre.sh
$(DST_PROBES_DIR)/polaris.sh: $(TRANSIENT_DIR)/polaris.sh
$(DST_PROBES_DIR)/sparta.sh: $(TRANSIENT_DIR)/sparta.sh
$(DST_PROBES_DIR)/verkehr.sh: $(TRANSIENT_DIR)/verkehr.sh
$(DST_PROBES_DIR)/zephyr.sh: $(TRANSIENT_DIR)/zephyr.sh
