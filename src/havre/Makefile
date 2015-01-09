IMAGE_NAME = aerofs/havre
DST_DIR = buildroot/opt/havre
RSC_DIR = resources

image: .backup-source .fix-source .gradle .restore-source $(DST_DIR)/havre.properties $(DST_DIR)/logback.xml
	docker build -t $(IMAGE_NAME) .

HAVRE = src/com/aerofs/havre/Havre.java
FIXES = $(HAVRE)

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/havre/dist $(DST_DIR)

.backup-source:
	for i in $(FIXES); do cp $$i /tmp/$$(basename $$i); done

.restore-source:
	for i in $(FIXES); do mv /tmp/$$(basename $$i) $$i; done

.fix-source:
	sed -i '' -e 's`getStringProperty("havre.oauth.url", "http://localhost:8700/tokeninfo")`"http://bifrost.service:8700/tokeninfo"`' \
		$(HAVRE)

$(DST_DIR)/%: $(RSC_DIR)/%
	mkdir -p $(@D)
	cp $< $@

$(DST_DIR)/havre.properties: $(RSC_DIR)/havre.properties
	mkdir -p $(@D)
	sed -e 's`/etc/ssl/certs/AeroFS_CA.pem`/opt/havre/cacert.pem`' \
		-e 's`havre.proxy.host = 127.0.0.1`havre.proxy.host = 0.0.0.0`' $< > $@
