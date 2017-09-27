SUBDIRS:=\
	base \
	jre8 \
	python2.7


all: image
%:
	for dir in $(SUBDIRS); do \
		make -C $$dir $@; \
	done
