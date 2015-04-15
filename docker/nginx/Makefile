IMAGE_NAME = aerofs/nginx

image: .fix-site-confs
	$(MAKE) -f ../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

# PREDOCKER
DIR = buildroot/etc/nginx/sites

SITES = sp \
	havre \
	sparta \
	bifrost \
	auditor \
	openid \
	polaris \
	status

.fix-site-confs: $(DIR) $(SITES:%=$(DIR)/%)

$(DIR):
	mkdir -p $@

$(DIR)/%:
	sed -e 's/127.0.0.1/$*.service/' ../../packaging/$*/etc/nginx/backends-available/aerofs-$* > $(DIR)/$*

# TODO (WW) Do not duplicate common configurations so f****** many times in these files
# Is there an easy way to specify the deps?
$(DIR)/sp: ../../packaging/sp/etc/nginx/backends-available/aerofs-sp
$(DIR)/havre: ../../packaging/havre/etc/nginx/backends-available/aerofs-havre
$(DIR)/sparta: ../../packaging/sparta/etc/nginx/backends-available/aerofs-sparta
$(DIR)/bifrost: ../../packaging/bifrost/etc/nginx/backends-available/aerofs-bifrost
$(DIR)/auditor: ../../packaging/auditor/etc/nginx/backends-available/aerofs-auditor
$(DIR)/polaris: ../../packaging/polaris/etc/nginx/backends-available/aerofs-polaris

$(DIR)/status: ../../packaging/sanity/etc/nginx/backends-available/aerofs-sanity
	sed -e 's/127.0.0.1/status.service/' \
	    -e 's`/opt/sanity/htpasswd`/status.htpasswd`' $< > $@

$(DIR)/openid: ../../packaging/identity/etc/nginx/backends-available/aerofs-identity
	sed -e 's/127.0.0.1/openid.service/' $< > $@

.PHONY: image .fix-site-confs
