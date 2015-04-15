IMAGE_NAME = aerofs/ejabberd

ROOT = buildroot
EBIN = $(ROOT)/usr/lib/ejabberd/ebin
ETC = $(ROOT)/etc/ejabberd

FILES = \
	$(EBIN)/mysql.beam \
	$(EBIN)/mysql_auth.beam \
	$(EBIN)/mysql_conn.beam \
	$(EBIN)/mysql_recv.beam \
	$(ETC)/mysql.sql \
	$(ETC)/ejabberd.cfg \
	$(ETC)/auth_all \
	$(ROOT)/etc/default/ejabberd \
	$(ROOT)/ejabberd_check

image: $(FILES)
	docker build -t $(IMAGE_NAME) .

$(EBIN)/%: ../../puppetmaster/modules/ejabberd/files/ebin/%
	mkdir -p $(@D)
	cp $< $@

$(ETC)/mysql.sql: ../../puppetmaster/modules/ejabberd/files/mysql.sql
	mkdir -p $(@D)
	cp $< $@

$(ETC)/ejabberd.cfg: ../../puppetmaster/modules/ejabberd/templates/ejabberd.cfg.erb
	mkdir -p $(@D)
	sed -e 's/<%= @port %>/5222/' \
	    -e 's/<%= @mysql_host %>/mysql.service/' \
	    -e 's/<%= @mysql_db %>/ejabberd/' \
	    -e 's/<%= @mysql_user %>/ejabberd/' \
	    -e 's/<%= @mysql_password %>/password/' $< > $@

$(ROOT)/etc/default/ejabberd: ../../puppetmaster/modules/ejabberd/files/default_ejabberd
	mkdir -p $(@D)
	cp $< $@

$(ROOT)/ejabberd_check: ../../puppetmaster/modules/ejabberd/files/ejabberd_check
	mkdir -p $(@D)
	cp $< $@
	chmod +x $@

$(ETC)/%: ../../puppetmaster/modules/ejabberd/files/%
	mkdir -p $(@D)
	cp $< $@

$(ETC)/auth_all: ../../puppetmaster/modules/ejabberd/files/auth_all
	mkdir -p $(@D)
	cp $< $@
	chmod +x $@

.PHONY: image
