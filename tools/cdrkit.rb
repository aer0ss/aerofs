require 'formula'

class Cdrkit < Formula
  homepage 'http://cdrkit.org/'
  url 'http://cdrkit.org/releases/cdrkit-1.1.11.tar.gz'
  sha1 '3f7ddc06db0272942e1a4cd98c3c96462df77387'

  depends_on 'cmake' => :build

  def patches; DATA; end

  def install
    system "cmake", ".", *std_cmake_args
    system "make", "install"
  end

  test do
    system "genisoimage", "--version"
  end
end
__END__
diff --git a/CMakeLists.txt b/CMakeLists.txt
index 57edba6..6fe8ebb 100644
--- a/CMakeLists.txt
+++ b/CMakeLists.txt
@@ -1,3 +1,6 @@
+cmake_minimum_required(VERSION 2.4)
+cmake_policy(SET CMP0015 OLD)
+cmake_policy(SET CMP0003 NEW)
 PROJECT (cdrkit C)
 SUBDIRS(include genisoimage wodim libedc libhfs_iso libparanoia icedax libusal librols libunls readom netscsid 3rd-party/dirsplit)
 
diff --git a/genisoimage/CMakeLists.txt b/genisoimage/CMakeLists.txt
index 303ba4d..141448a 100644
--- a/genisoimage/CMakeLists.txt
+++ b/genisoimage/CMakeLists.txt
@@ -1,5 +1,13 @@
 PROJECT (MKISOFS C)
 
+IF (APPLE)
+	FIND_LIBRARY(IOKIT_LIBRARY NAMES IOKit)
+	FIND_LIBRARY(COREFOUNDATION_LIBRARY NAMES CoreFoundation)
+	LIST(APPEND EXTRA_LIBS ${IOKIT_LIBRARY} ${COREFOUNDATION_LIBRARY} )
+ENDIF(APPLE)
+
+
+
 INCLUDE(../include/AddScgBits.cmake)
 INCLUDE(../include/AddSchilyBits.cmake)
 
diff --git a/genisoimage/endian.h b/genisoimage/endian.h
new file mode 100644
index 0000000..ff973e8
--- /dev/null
+++ b/genisoimage/endian.h
@@ -0,0 +1,2 @@
+#define __BYTE_ORDER __LITTLE_ENDIAN
+#define __THROW
diff --git a/genisoimage/sha256.c b/genisoimage/sha256.c
index f7558b2..2d94ac6 100644
--- a/genisoimage/sha256.c
+++ b/genisoimage/sha256.c
@@ -24,7 +24,7 @@
 
 /* Written by Ulrich Drepper <drepper@redhat.com>, 2007.  */
 
-#include <endian.h>
+#include "endian.h"
 #include <stdlib.h>
 #include <string.h>
 #include <sys/types.h>
diff --git a/genisoimage/sha256.h b/genisoimage/sha256.h
index e7f4cb9..27c31f0 100644
--- a/genisoimage/sha256.h
+++ b/genisoimage/sha256.h
@@ -28,6 +28,7 @@
 #include <limits.h>
 #include <stdint.h>
 #include <stdio.h>
+#include "endian.h"
 
 
 /* Structure to save state of computation between the single steps.  */
diff --git a/genisoimage/sha512.c b/genisoimage/sha512.c
index c4987ce..da16def 100644
--- a/genisoimage/sha512.c
+++ b/genisoimage/sha512.c
@@ -24,7 +24,7 @@
 
 /* Written by Ulrich Drepper <drepper@redhat.com>, 2007.  */
 
-#include <endian.h>
+#include "endian.h"
 #include <stdlib.h>
 #include <string.h>
 #include <sys/types.h>
diff --git a/genisoimage/sha512.h b/genisoimage/sha512.h
index 7298355..691103a 100644
--- a/genisoimage/sha512.h
+++ b/genisoimage/sha512.h
@@ -28,6 +28,7 @@
 #include <limits.h>
 #include <stdint.h>
 #include <stdio.h>
+#include "endian.h"
 
 
 /* Structure to save state of computation between the single steps.  */
diff --git a/icedax/CMakeLists.txt b/icedax/CMakeLists.txt
index 54c2e7d..777be8c 100644
--- a/icedax/CMakeLists.txt
+++ b/icedax/CMakeLists.txt
@@ -1,4 +1,9 @@
 PROJECT (icedax C)
+IF (APPLE)
+       FIND_LIBRARY(IOKIT_LIBRARY NAMES IOKit)
+       FIND_LIBRARY(COREFOUNDATION_LIBRARY NAMES CoreFoundation)
+       LIST(APPEND EXTRA_LIBS ${IOKIT_LIBRARY} ${COREFOUNDATION_LIBRARY} )
+ENDIF(APPLE)
 INCLUDE_DIRECTORIES(../include ../wodim ../libparanoia ${CMAKE_BINARY_DIR} ${CMAKE_BINARY_DIR}/include)
 include(../include/AddScgBits.cmake)
 include(../include/AddSchilyBits.cmake)
diff --git a/include/AddScgBits.cmake b/include/AddScgBits.cmake
index 6aaff73..061f7f8 100644
--- a/include/AddScgBits.cmake
+++ b/include/AddScgBits.cmake
@@ -58,7 +58,7 @@ SET(CMAKE_REQUIRED_LIBRARIES )
    CHECK_C_SOURCE_COMPILES("${TESTSRC}" LIBC_SCHED)
 
 IF(NOT LIBC_SCHED)
-   LIST(APPEND EXTRA_LIBS -lrt)
+	#LIST(APPEND EXTRA_LIBS -lrt)
    #MESSAGE("Using librt for realtime functions")
 ENDIF(NOT LIBC_SCHED)
 
diff --git a/libusal/scsi-mac-iokit.c b/libusal/scsi-mac-iokit.c
index 7e5ee4f..553d858 100644
--- a/libusal/scsi-mac-iokit.c
+++ b/libusal/scsi-mac-iokit.c
@@ -62,7 +62,7 @@ static	char	_usal_trans_version[] = "scsi-mac-iokit.c-1.10";	/* The version for
 #include <Carbon/Carbon.h>
 #include <IOKit/IOKitLib.h>
 #include <IOKit/IOCFPlugIn.h>
-#include <IOKit/scsi-commands/SCSITaskLib.h>
+#include <IOKit/scsi/SCSITaskLib.h>
 #include <mach/mach_error.h>
 
 struct usal_local {
diff --git a/netscsid/CMakeLists.txt b/netscsid/CMakeLists.txt
index f6bddf1..572aba6 100644
--- a/netscsid/CMakeLists.txt
+++ b/netscsid/CMakeLists.txt
@@ -1,4 +1,9 @@
 PROJECT (netscsid C)
+IF (APPLE)
+       FIND_LIBRARY(IOKIT_LIBRARY NAMES IOKit)
+       FIND_LIBRARY(COREFOUNDATION_LIBRARY NAMES CoreFoundation)
+       LIST(APPEND EXTRA_LIBS ${IOKIT_LIBRARY} ${COREFOUNDATION_LIBRARY} )
+ENDIF(APPLE)
 INCLUDE_DIRECTORIES(../include ../wodim ${CMAKE_BINARY_DIR} ${CMAKE_BINARY_DIR}/include)
 INCLUDE(../include/AddScgBits.cmake)
 INCLUDE(../include/AddSchilyBits.cmake)
diff --git a/readom/CMakeLists.txt b/readom/CMakeLists.txt
index 81536d8..40f4e23 100644
--- a/readom/CMakeLists.txt
+++ b/readom/CMakeLists.txt
@@ -1,4 +1,9 @@
 PROJECT (READECD C)
+IF (APPLE)
+       FIND_LIBRARY(IOKIT_LIBRARY NAMES IOKit)
+       FIND_LIBRARY(COREFOUNDATION_LIBRARY NAMES CoreFoundation)
+       LIST(APPEND EXTRA_LIBS ${IOKIT_LIBRARY} ${COREFOUNDATION_LIBRARY} )
+ENDIF(APPLE)
 INCLUDE_DIRECTORIES(../include ../wodim ${CMAKE_BINARY_DIR} ${CMAKE_BINARY_DIR}/include)
 INCLUDE(../include/AddScgBits.cmake)
 INCLUDE(../include/AddSchilyBits.cmake)
diff --git a/wodim/CMakeLists.txt b/wodim/CMakeLists.txt
index d6245c9..dee0ad3 100644
--- a/wodim/CMakeLists.txt
+++ b/wodim/CMakeLists.txt
@@ -1,4 +1,11 @@
 PROJECT (CDRECORD C)
+
+IF (APPLE)
+       FIND_LIBRARY(IOKIT_LIBRARY NAMES IOKit)
+       FIND_LIBRARY(COREFOUNDATION_LIBRARY NAMES CoreFoundation)
+       LIST(APPEND EXTRA_LIBS ${IOKIT_LIBRARY} ${COREFOUNDATION_LIBRARY} )
+ENDIF(APPLE)
+
 INCLUDE_DIRECTORIES(../include ../libedc ${CMAKE_BINARY_DIR} ${CMAKE_BINARY_DIR}/include)
 INCLUDE(../include/AddScgBits.cmake)
 include(../include/AddSchilyBits.cmake)
