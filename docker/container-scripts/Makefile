FILES = service-barrier \
	import-ca-cert-to-java \
	copy-ca-cert \
	certify \
	get-config-property \
	set-config-property \
	create-database \
	crt-create \
	restart-on-error

SRC_DIR = $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
DST_DIR = buildroot/container-scripts

copy-scripts: $(DST_DIR) $(FILES:%=$(DST_DIR)/%)

$(DST_DIR):
	mkdir -p $@

$(DST_DIR)/%: $(SRC_DIR)/%
	cp $< $@

.PHONY: copy-scripts
