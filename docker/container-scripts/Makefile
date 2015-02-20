FILES = service-barrier \
	import-ca-cert-to-java \
	copy-ca-cert \
	certify \
	get-config-property \
	set-config-property \
	create-database \
	crt-create

SRC_DIR = $(realpath $(dir $(lastword $(MAKEFILE_LIST))))
DST_DIR = buildroot/container-scripts

copy-scripts: $(DST_DIR) $(FILES:%=$(DST_DIR)/%)

$(DST_DIR):
	mkdir -p $@

$(DST_DIR)/%: $(SRC_DIR)/%
	cp $< $@

# PREDOCKER
$(DST_DIR)/crt-create: $(SRC_DIR)/../../packaging/ca-tools/usr/bin/crt-create
	sed -e 's|CA_HOST:1029|CA_HOST:9002|' $< > $@
	chmod a+x $@
