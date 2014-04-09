require 'formula'

class OpensslAerofs < Formula
  homepage 'http://openssl.org'
  url 'http://openssl.org/source/openssl-1.0.1g.tar.gz'
  sha256 '53cb818c3b90e507a8348f4f5eaedb05d8bfe5358aabb508b7263cc670c3e028'

  keg_only :provided_by_osx,
    "The OpenSSL provided by OS X is too old for some software."

  def patches
    # Makes libcrypto and libssl not specify absolute RPATHs
    DATA
  end

  def install
    args = %W[./Configure
               --prefix=#{prefix}
               --openssldir=#{etc}/openssl
               shared
             ]

    args << (MacOS.prefer_64_bit? ? "darwin64-x86_64-cc" : "darwin-i386-cc")

    system "perl", *args

    ENV.deparallelize # Parallel compilation fails
    system "make"
    system "make", "test"
    prefix.install 'libcrypto.1.0.0.dylib', 'libssl.1.0.0.dylib'
    #system "make", "install", "MANDIR=#{man}", "MANSUFFIX=ssl"
  end
end

__END__
--- a/Makefile.shared	2012-09-24 18:56:25.000000000 -0700
+++ b/Makefile.shared	2010-08-21 04:37:17.000000000 -0700
@@ -250,7 +250,7 @@
 	if [ -n "$$SHLIB_SOVER_NODOT" ]; then \
 		SHAREDFLAGS="$$SHAREDFLAGS -compatibility_version $$SHLIB_SOVER_NODOT"; \
 	fi; \
-	SHAREDFLAGS="$$SHAREDFLAGS -install_name $(INSTALLTOP)/$(LIBDIR)/$$SHLIB$(SHLIB_EXT)"; \
+	SHAREDFLAGS="$$SHAREDFLAGS -install_name $$SHLIB$(SHLIB_EXT)"; \
 	$(LINK_SO_A)
 link_app.darwin:	# is there run-path on darwin?
 	$(LINK_APP)
--- a/Configure   2012-10-03 14:35:24.000000000 -0700
+++ b/Configure   2012-03-14 15:20:40.000000000 -0700
@@ -579,7 +579,7 @@
 "darwin64-ppc-cc","cc:-arch ppc64 -O3 -DB_ENDIAN::-D_REENTRANT:MACOSX:-Wl,-search_paths_first%:SIXTY_FOUR_BIT_LONG RC4_CHAR RC4_CHUNK DES_UNROLL BF_PTR:${ppc64_asm}:osx64:dlfcn:darwin-shared:-fPIC -fno-common:-arch ppc64 -dynamiclib:.\$(SHLIB_MAJOR).\$(SHLIB_MINOR).dylib",
 "darwin-i386-cc","cc:-arch i386 -O3 -fomit-frame-pointer -DL_ENDIAN::-D_REENTRANT:MACOSX:-Wl,-search_paths_first%:BN_LLONG RC4_INT RC4_CHUNK DES_UNROLL BF_PTR:".eval{my $asm=$x86_asm;$asm=~s/cast\-586\.o//;$asm}.":macosx:dlfcn:darwin-shared:-fPIC -fno-common:-arch i386 -dynamiclib:.\$(SHLIB_MAJOR).\$(SHLIB_MINOR).dylib",
 "debug-darwin-i386-cc","cc:-arch i386 -g3 -DL_ENDIAN::-D_REENTRANT:MACOSX:-Wl,-search_paths_first%:BN_LLONG RC4_INT RC4_CHUNK DES_UNROLL BF_PTR:${x86_asm}:macosx:dlfcn:darwin-shared:-fPIC -fno-common:-arch i386 -dynamiclib:.\$(SHLIB_MAJOR).\$(SHLIB_MINOR).dylib",
-"darwin64-x86_64-cc","cc:-arch x86_64 -O3 -DL_ENDIAN -Wall::-D_REENTRANT:MACOSX:-Wl,-search_paths_first%:SIXTY_FOUR_BIT_LONG RC4_CHAR RC4_CHUNK DES_INT DES_UNROLL:".eval{my $asm=$x86_64_asm;$asm=~s/rc4\-[^:]+//;$asm}.":macosx:dlfcn:darwin-shared:-fPIC -fno-common:-arch x86_64 -dynamiclib:.\$(SHLIB_MAJOR).\$(SHLIB_MINOR).dylib",
+"darwin64-x86_64-cc","cc:-arch x86_64 -O3 -mno-avx -mmacosx-version-min=10.5 -DL_ENDIAN -Wall::-D_REENTRANT:MACOSX:-Wl,-search_paths_first%:SIXTY_FOUR_BIT_LONG RC4_CHAR RC4_CHUNK DES_INT DES_UNROLL:".eval{my $asm=$x86_64_asm;$asm=~s/rc4\-[^:]+//;$asm}.":macosx:dlfcn:darwin-shared:-fPIC -fno-common:-arch x86_64 -dynamiclib:.\$(SHLIB_MAJOR).\$(SHLIB_MINOR).dylib",
 "debug-darwin-ppc-cc","cc:-DBN_DEBUG -DREF_CHECK -DCONF_DEBUG -DCRYPTO_MDEBUG -DB_ENDIAN -g -Wall -O::-D_REENTRANT:MACOSX::BN_LLONG RC4_CHAR RC4_CHUNK DES_UNROLL BF_PTR:${ppc32_asm}:osx32:dlfcn:darwin-shared:-fPIC:-dynamiclib:.\$(SHLIB_MAJOR).\$(SHLIB_MINOR).dylib",
 # iPhoneOS/iOS
 "iphoneos-cross","llvm-gcc:-O3 -isysroot \$(CROSS_TOP)/SDKs/\$(CROSS_SDK) -fomit-frame-pointer -fno-common::-D_REENTRANT:iOS:-Wl,-search_paths_first%:BN_LLONG RC4_CHAR RC4_CHUNK DES_UNROLL BF_PTR:${no_asm}:dlfcn:darwin-shared:-fPIC -fno-common:-dynamiclib:.\$(SHLIB_MAJOR).\$(SHLIB_MINOR).dylib",
