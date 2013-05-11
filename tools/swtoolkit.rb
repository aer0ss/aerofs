require 'formula'

class Swtoolkit < Formula
  url 'https://swtoolkit.googlecode.com/files/swtoolkit.0.9.1.zip'
  homepage 'http://code.google.com/p/swtoolkit/'
  sha256 'a43436af6ffad0b94a7797b12c1e7b479cc95eef9bcead6338e56305b052f052'

  def patches
    DATA
  end

  def install
    bin.install    'hammer.sh'
    prefix.install 'wrapper.py', 'site_scons'
  end
end

__END__
--- a/hammer.sh    2009-05-04 10:56:34.000000000 -0700
+++ b/hammer.sh    2013-05-10 19:14:16.000000000 -0700
@@ -54,8 +54,8 @@
 #   -s -k
 #      Don't print commands; keep going on build failures.

-export SCT_DIR="$(dirname -- "${0}")"
-export PYTHONPATH="$SCONS_DIR"
+export SCT_DIR="$(brew --prefix)/Cellar/swtoolkit/0.9.1"
+export PYTHONPATH="$(brew --prefix)/Cellar/scons/2.0.1/lib/scons"

 # Invoke scons via the software construction toolkit wrapper.
 python $COVERAGE_HOOK "${SCT_DIR}/wrapper.py" $HAMMER_OPTS --site-dir="${SCT_DIR}/site_scons" "$@"
