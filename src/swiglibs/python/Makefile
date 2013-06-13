#################################################################
# This makefile works on Cygwin only, to create libScryptPy.dll #
# It will be updated in the future to work on OSX and Linux     #
#################################################################

CC=gcc
CFLAGS=-Wall
SOURCES=crypto_aesctr.c crypto_scrypt-nosse.c sha256.c ScryptPy.c
OBJECTS=$(SOURCES:.c=.o)
TARGET=libScryptPy.dll
INCLUDEPATH=-IC:/cygwin/usr/include/python2.7 -I../3rd_party/include -I../src/scrypt
LIBS=-LC:/cygwin/lib/python2.7/config -L../3rd_party/lib/win32/ -llibeay32 -lpython2.7
INSTALLPATH=../../python-lib/aerofs_sp/lib/win/

vpath %.c ../src/scrypt

all: $(SOURCES) $(TARGET) install

clean:
    rm -rf $(OBJECTS) $(TARGET)

%.o: %.c
    $(CC) $(CFLAGS) $(INCLUDEPATH) -c $^

$(TARGET): $(OBJECTS)
    $(CC) -shared $(OBJECTS) $(LIBS) -o $(TARGET)

install:
    cp $(TARGET) $(INSTALLPATH)
