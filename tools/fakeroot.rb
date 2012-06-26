require 'formula'

class Fakeroot < Formula
  homepage 'http://packages.qa.debian.org/f/fakeroot.html'
  url 'http://ftp.debian.org/debian/pool/main/f/fakeroot/fakeroot_1.18.4.orig.tar.bz2'
  md5 '706171d8d520b1ca1576ac73f2ceb4f3'

  depends_on 'libtool'

  def patches
    DATA
  end
  def install
    system "./bootstrap"
    system "./configure", "--disable-dependency-tracking", "--disable-static",
                          "--prefix=#{prefix}"
    system "make"
    system "make check"
    system "make install"
  end
end

__END__
diff --git a/doc/Makefile.am b/doc/Makefile.am
index bf5ce07..31f2717 100644
--- a/doc/Makefile.am
+++ b/doc/Makefile.am
@@ -1,5 +1,5 @@
 AUTOMAKE_OPTIONS=foreign
-SUBDIRS = de es fr sv nl
+#SUBDIRS = de es fr sv nl

 man_MANS = faked.1 fakeroot.1
