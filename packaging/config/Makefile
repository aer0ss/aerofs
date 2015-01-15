IMAGE_NAME = aerofs/config
ROOT = buildroot

# PREDOCKER
DEFAULT_PROPS_FILE = $(ROOT)/external.properties.default

FILES = $(shell find -L opt etc -type f)
# /opt/config/properties/ will be bind mounted with the data container
FILES := $(filter-out opt/config/properties/%, $(FILES))

LICENSING_LIB_DIR = ../../src/licensing

image: $(FILES:%=$(ROOT)/%) $(DEFAULT_PROPS_FILE)
	$(MAKE) -C $(LICENSING_LIB_DIR)
	../../tools/lazy-copy.sh $(LICENSING_LIB_DIR)/build $(ROOT)/sdist
	docker build -t $(IMAGE_NAME) .

$(ROOT)/%: %
	mkdir -p $(@D)
	cp $< $@

$(ROOT)/opt/config/main.py: opt/config/main.py main.docker.patch
	mkdir -p $(@D)
	cp $< $@
	patch $@ < main.docker.patch

$(ROOT)/opt/config/entry.py: opt/config/entry.py
	mkdir -p $(@D)
	sed -e 's/127\.0\.0\.1/0.0.0.0/' $< > $@

$(ROOT)/opt/config/templates/common.tmplt: opt/config/templates/common.tmplt
	mkdir -p $(@D)
	cp $< $@
	echo "docker=1" >> $@

$(ROOT)/opt/config/templates/server.tmplt: opt/config/templates/server.tmplt
	mkdir -p $(@D)
	grep -v 'base.configuration.initialized' $< > $@

$(DEFAULT_PROPS_FILE): opt/config/properties/external.properties external.properties.docker.patch
	mkdir -p $(@D)
	cp $< $@
	patch $@ < external.properties.docker.patch
