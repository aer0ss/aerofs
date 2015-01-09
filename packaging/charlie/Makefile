IMAGE_NAME = aerofs/charlie
ROOT = buildroot

# PREDOCKER
FILES = $(shell find -L opt etc -type f)
CONFIG_FILE = $(ROOT)/opt/charlie/config.py
PYTHON_LIB_DIR = ../../src/python-lib

image: $(FILES:%=$(ROOT)/%) $(CONFIG_FILE)
	$(MAKE) -C $(PYTHON_LIB_DIR)
	../../tools/lazy-copy.sh "$(PYTHON_LIB_DIR)/build" "buildroot/sdist"
	docker build -t $(IMAGE_NAME) .

$(ROOT)/%: %
	mkdir -p $(@D)
	cp $< $@

$(ROOT)/opt/charlie/charles.py: opt/charlie/charles.py
	mkdir -p $(@D)
	sed -e 's`config_client.client_properties()`{ "base.verkehr.host": "verkehr.service", "base.verkehr.port.rest": 25234 }`' $< > $@

$(CONFIG_FILE):
	mkdir -p $(@D)
	echo "CONFIG_SERVER_BASE_URI='dont_care'" > $@
