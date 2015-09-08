SUBDIRS:=\
	base \
	jre8 \
	jre8-and-mysql-client \
	python2.7


all: image
%:
	for dir in $(SUBDIRS); do \
		make -C $$dir $@; \
	done
