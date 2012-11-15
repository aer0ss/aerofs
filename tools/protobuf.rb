require 'formula'

class Protobuf < Formula
  homepage 'http://code.google.com/p/protobuf/'
  url 'http://protobuf.googlecode.com/files/protobuf-2.4.1.tar.bz2'
  sha1 'df5867e37a4b51fb69f53a8baf5b994938691d6d'

  option :universal

  fails_with :llvm do
    build 2334
  end

  def patches
    # Avoid generating code that will produce warnings when compiled.
    # Patch taken from the upstream bug report:
    # http://code.google.com/p/protobuf/issues/detail?id=266
    # + modifications to also patch java_enum_field.cc
    DATA
  end

  def install
    # Don't build in debug mode. See:
    # https://github.com/mxcl/homebrew/issues/9279
    # http://code.google.com/p/protobuf/source/browse/trunk/configure.ac#61
    ENV.prepend 'CXXFLAGS', '-DNDEBUG'
    ENV.universal_binary if build.universal?
    system "./configure", "--disable-debug", "--disable-dependency-tracking",
                          "--prefix=#{prefix}",
                          "--with-zlib"
    system "make"
    system "make install"

    # Install editor support and examples
    doc.install %w( editors examples )
  end

  def caveats; <<-EOS.undent
    Editor support and examples have been installed to:
      #{doc}/protobuf
    EOS
  end
end
__END__
Index: src/google/protobuf/compiler/java/java_enum_field.cc
===================================================================
--- a/src/google/protobuf/compiler/java/java_enum_field.cc     (revision 381)
+++ b/src/google/protobuf/compiler/java/java_enum_field.cc     (working copy)
@@ -83,6 +83,8 @@
 
   // For repated builders, one bit is used for whether the array is immutable.
   (*variables)["get_mutable_bit_builder"] = GenerateGetBit(builderBitIndex);
+  (*variables)["get_mutable_bit_builder_from_local"] =
+      GenerateGetBitFromLocal(builderBitIndex);
   (*variables)["set_mutable_bit_builder"] = GenerateSetBit(builderBitIndex);
   (*variables)["clear_mutable_bit_builder"] = GenerateClearBit(builderBitIndex);
 
@@ -411,7 +413,7 @@
   // The code below ensures that the result has an immutable list. If our
   // list is immutable, we can just reuse it. If not, we make it immutable.
   printer->Print(variables_,
-    "if ($get_mutable_bit_builder$) {\n"
+    "if ($get_mutable_bit_builder_from_local$) {\n"
     "  $name$_ = java.util.Collections.unmodifiableList($name$_);\n"
     "  $clear_mutable_bit_builder$;\n"
     "}\n"
Index: src/google/protobuf/compiler/java/java_string_field.cc
===================================================================
--- a/src/google/protobuf/compiler/java/java_string_field.cc	(revision 381)
+++ b/src/google/protobuf/compiler/java/java_string_field.cc	(working copy)
@@ -93,6 +93,8 @@
 
   // For repated builders, one bit is used for whether the array is immutable.
   (*variables)["get_mutable_bit_builder"] = GenerateGetBit(builderBitIndex);
+  (*variables)["get_mutable_bit_builder_from_local"] = 
+      GenerateGetBitFromLocal(builderBitIndex);
   (*variables)["set_mutable_bit_builder"] = GenerateSetBit(builderBitIndex);
   (*variables)["clear_mutable_bit_builder"] = GenerateClearBit(builderBitIndex);
 
@@ -496,7 +498,7 @@
   // list is immutable, we can just reuse it. If not, we make it immutable.
 
   printer->Print(variables_,
-    "if ($get_mutable_bit_builder$) {\n"
+    "if ($get_mutable_bit_builder_from_local$) {\n"
     "  $name$_ = new com.google.protobuf.UnmodifiableLazyStringList(\n"
     "      $name$_);\n"
     "  $clear_mutable_bit_builder$;\n"
Index: src/google/protobuf/compiler/java/java_primitive_field.cc
===================================================================
--- a/src/google/protobuf/compiler/java/java_primitive_field.cc	(revision 381)
+++ b/src/google/protobuf/compiler/java/java_primitive_field.cc	(working copy)
@@ -205,6 +205,8 @@
 
   // For repated builders, one bit is used for whether the array is immutable.
   (*variables)["get_mutable_bit_builder"] = GenerateGetBit(builderBitIndex);
+  (*variables)["get_mutable_bit_builder_from_local"] = 
+      GenerateGetBitFromLocal(builderBitIndex);
   (*variables)["set_mutable_bit_builder"] = GenerateSetBit(builderBitIndex);
   (*variables)["clear_mutable_bit_builder"] = GenerateClearBit(builderBitIndex);
 
@@ -606,7 +608,7 @@
   // The code below ensures that the result has an immutable list. If our
   // list is immutable, we can just reuse it. If not, we make it immutable.
   printer->Print(variables_,
-    "if ($get_mutable_bit_builder$) {\n"
+    "if ($get_mutable_bit_builder_from_local$) {\n"
     "  $name$_ = java.util.Collections.unmodifiableList($name$_);\n"
     "  $clear_mutable_bit_builder$;\n"
     "}\n"
Index: src/google/protobuf/compiler/java/java_message_field.cc
===================================================================
--- a/src/google/protobuf/compiler/java/java_message_field.cc	(revision 381)
+++ b/src/google/protobuf/compiler/java/java_message_field.cc	(working copy)
@@ -81,6 +81,8 @@
 
   // For repated builders, one bit is used for whether the array is immutable.
   (*variables)["get_mutable_bit_builder"] = GenerateGetBit(builderBitIndex);
+  (*variables)["get_mutable_bit_builder_from_local"] = 
+      GenerateGetBitFromLocal(builderBitIndex);
   (*variables)["set_mutable_bit_builder"] = GenerateSetBit(builderBitIndex);
   (*variables)["clear_mutable_bit_builder"] = GenerateClearBit(builderBitIndex);
 
@@ -815,7 +817,7 @@
   // immutable list. If our list is immutable, we can just reuse it. If not,
   // we make it immutable.
   PrintNestedBuilderCondition(printer,
-    "if ($get_mutable_bit_builder$) {\n"
+    "if ($get_mutable_bit_builder_from_local$) {\n"
     "  $name$_ = java.util.Collections.unmodifiableList($name$_);\n"
     "  $clear_mutable_bit_builder$;\n"
     "}\n"
