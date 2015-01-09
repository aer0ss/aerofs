IMAGE_NAME = aerofs/bifrost
DST_DIR = buildroot/opt/bifrost
RSC_DIR = resources

# PREDOCKER
image: .backup-source .fix-source .gradle .restore-source $(DST_DIR)/bifrost.properties $(DST_DIR)/logback.xml
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/bifrost/dist $(DST_DIR)

BASE_PARAM = ../base/src/main/java/com/aerofs/base/BaseParam.java
FIXES = $(BASE_PARAM)

.backup-source:
	for i in $(FIXES); do cp $$i /tmp/$$(basename $$i); done

.restore-source:
	for i in $(FIXES); do mv /tmp/$$(basename $$i) $$i; done

$(DST_DIR)/%: $(RSC_DIR)/%
	mkdir -p $(@D)
	cp $< $@

$(DST_DIR)/bifrost.properties: $(RSC_DIR)/bifrost.properties
	mkdir -p $(@D)
	sed -e 's/localhost/mysql.service/' $< > $@

.fix-source:
	sed -i '' \
	    -e 's`/etc/ssl/certs/AeroFS_CA.pem`/opt/bifrost/cacert.pem`' \
	    -e 's`getUrlProperty("base.sp.url",`getURL();`' \
	    -e 's`"https://sp.aerofs.com/sp/");``' \
	    -e 's`public static class SP`\
		static URL getURL() \
		{ \
		    try { \
			return new URL("http://sp.service:8080"); \
		    } catch (java.net.MalformedURLException e) { \
			throw com.google.common.base.Throwables.propagate(e); \
		    } \
		} \
		public static class  SP`' \
	    $(BASE_PARAM)
