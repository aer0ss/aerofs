#!/usr/bin/env python3
import os
import shutil
import subprocess
import sys
import tempfile


PROTOC = '/usr/local/bin/protoc'


GIT_ROOT = os.path.join(os.path.abspath(os.path.dirname(__file__)), '..', '..')

BUILD_DIR = os.path.join(GIT_ROOT, 'out.shell')
SRC_DIR = os.path.join(GIT_ROOT, 'src')
TOOLS_DIR = os.path.join(GIT_ROOT, 'tools')

SRC_BASE = os.path.join(SRC_DIR, 'base')
SRC_GUI = os.path.join(SRC_DIR, 'gui')
SRC_LIBCLIENT = os.path.join(SRC_DIR, 'libclient')
SRC_PYTHONLIB = os.path.join(SRC_DIR, 'python-lib')
SRC_SHELLEXT = os.path.join(SRC_DIR, 'shellext')
SRC_SYNC = os.path.join(SRC_DIR, 'sync')
SRC_ZEPHYR = os.path.join(SRC_DIR, 'zephyr')

SRC_AEROFS_COMMON = os.path.join(SRC_PYTHONLIB, 'aerofs_common')
SRC_AEROFS_RITUAL = os.path.join(SRC_PYTHONLIB, 'aerofs_ritual')
SRC_AEROFS_SP = os.path.join(SRC_PYTHONLIB, 'aerofs_sp')


# map from <destdir> to <map of language, includes, proto files>
PROTO_MAPPINGS = {
    os.path.join(SRC_BASE, 'gen'): {
        'lang': 'java',
        'includes': [os.path.join(SRC_BASE, 'src', 'proto')],
        'protos': [
            os.path.join(TOOLS_DIR, 'protobuf.plugins', 'proto', 'rpc_service.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'common.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'sp.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'cmd.proto'),
        ],
    },
    os.path.join(SRC_LIBCLIENT, 'gen'): {
        'lang': 'java',
        'includes': [
            os.path.join(SRC_BASE, 'src', 'proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'diagnostics.proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'path_status.proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'ritual_notifications.proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'ritual.proto'),
        ],
    },
    os.path.join(SRC_SYNC, 'gen'): {
        'lang': 'java',
        'includes': [
            os.path.join(SRC_BASE, 'src', 'proto'),
            os.path.join(SRC_SYNC, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(SRC_SYNC, 'src', 'proto', 'core.proto'),
            os.path.join(SRC_SYNC, 'src', 'proto', 'transport.proto'),
        ],
    },
    os.path.join(SRC_GUI, 'gen'): {
        'lang': 'java',
        'includes': [
            os.path.join(SRC_BASE, 'src', 'proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto'),
            os.path.join(SRC_GUI, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(SRC_GUI, 'src', 'proto', 'shellext.proto'),
        ],
    },
    os.path.join(SRC_ZEPHYR, 'gen'): {
        'lang': 'java',
        'includes': [
            os.path.join(SRC_ZEPHYR, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(SRC_ZEPHYR, 'src', 'proto', 'zephyr.proto'),
        ],
    },
    os.path.join(SRC_AEROFS_COMMON, '_gen'): {
        'lang': 'python',
        'includes': [
            os.path.join(TOOLS_DIR, 'protobuf.plugins', 'proto'),
            os.path.join(SRC_BASE, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(TOOLS_DIR, 'protobuf.plugins', 'proto', 'rpc_service.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'common.proto'),
        ],
    },
    os.path.join(SRC_AEROFS_SP, 'gen'): {
        'lang': 'python',
        'includes': [
            os.path.join(TOOLS_DIR, 'protobuf.plugins', 'proto'),
            os.path.join(SRC_BASE, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(TOOLS_DIR, 'protobuf.plugins', 'proto', 'rpc_service.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'common.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'sp.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'cmd.proto'),
        ],
    },
    os.path.join(SRC_AEROFS_RITUAL, 'gen'): {
        'lang': 'python',
        'includes': [
            os.path.join(TOOLS_DIR, 'protobuf.plugins', 'proto'),
            os.path.join(SRC_BASE, 'src', 'proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'diagnostics.proto'),
            os.path.join(SRC_BASE, 'src', 'proto', 'common.proto'),
            os.path.join(TOOLS_DIR, 'protobuf.plugins', 'proto', 'rpc_service.proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'path_status.proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'ritual.proto'),
        ],
    },
    os.path.join(SRC_SHELLEXT, 'osx_common', 'gen'): {
        'lang': 'objc',
        'includes': [
            os.path.join(SRC_BASE, 'src', 'proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto'),
            os.path.join(SRC_GUI, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'path_status.proto'),
            os.path.join(SRC_GUI, 'src', 'proto', 'shellext.proto'),
        ],
    },
    os.path.join(SRC_SHELLEXT, 'win_explorer', 'gen'): {
        'lang': 'cpp',
        'includes': [
            os.path.join(SRC_BASE, 'src', 'proto'),
            os.path.join(SRC_LIBCLIENT, 'src', 'proto'),
            os.path.join(SRC_GUI, 'src', 'proto'),
        ],
        'protos': [
            os.path.join(SRC_LIBCLIENT, 'src', 'proto', 'path_status.proto'),
            os.path.join(SRC_GUI, 'src', 'proto', 'shellext.proto'),
        ],
    },
}


def _mkdir_p(path):
    try:
        os.makedirs(path)
    except OSError:
        pass


def proto_cpp(output_dir, includes, protos):
    args_run = [PROTOC, '--cpp_out={}'.format(output_dir)]
    args_inc = ['-I{}'.format(include) for include in includes]
    subprocess.check_call(args_run + args_inc + protos)


def proto_java(output_dir, includes, protos):
    args_run = [
        PROTOC,
        '--plugin={}/protobuf-rpc/gen_rpc_java/protoc-gen-rpc-java'.format(
            BUILD_DIR),
        '--java_out={}'.format(output_dir),
        '--rpc-java_out={}'.format(output_dir),
        '-I{}/protobuf.plugins/proto'.format(TOOLS_DIR),
    ]
    args_inc = ['-I{}'.format(include) for include in includes]
    subprocess.check_call(args_run + args_inc + protos)

    # The code generated by protobuf has some, uh, quirks, which make it
    # trigger compiler warnings.  This makes protobuf generated code
    # incompatible with -Werror.
    # We fix up the generated java files with some find/replace magic.
    def _fixup_gen_java_file(path):
        with open(path) as f:
            with tempfile.NamedTemporaryFile(dir=os.path.dirname(path),
                                             delete=False) as g:
                pkg = None
                while True:
                    line = f.readline()
                    if line == "":
                        break

                    # minify: strip leading and trailing spaces
                    line = line.strip(' \t')

                    # minify: strip multiline comments
                    if line.startswith("/*"):
                        line = line[2:]
                        idx = line.find("*/")
                        while line != "" and idx == -1:
                            line = f.readline()
                            idx = line.find("*/")
                        if idx == -1:
                            break
                        line = line[idx+2:]

                    # minify: skip empty lines
                    if line == "\n" or line.startswith("//"):
                        continue

                    # 1) Suppress all warnings in generated protobuf files, so
                    # we can compile with -Werror
                    if line.startswith("public final class "):
                        line = '@SuppressWarnings("all") ' + line

                    # 2) Fix for the "using raw type" warning
                    line = line.replace(
                        "com.google.protobuf.GeneratedMessageLite.Builder builder",
                        "com.google.protobuf.GeneratedMessageLite.Builder<?,?> builder")

                    # 3) Fix warning about use of "super" in static methods
                    line = line.replace("super.addAll(", "Builder.addAll(")

                    # minify: simplify fully-qualified names
                    if pkg:
                        line = line.replace("com.google.protobuf.", "")
                        line = line.replace("com.google.common.util.concurrent.Futures.", "")
                        line = line.replace("com.google.common.util.concurrent.", "")
                        line = line.replace("java.lang.", "")
                        line = line.replace("java.util.concurrent.", "")
                        line = line.replace("java.util.", "")
                        line = line.replace("java.io.IOException", "IOException")
                        line = line.replace(pkg, "")

                    # minify: shrink common variable names
                    line = line.replace("bitField0_", "b0_")
                    line = line.replace("memoizedIsInitialized", "mii")
                    line = line.replace("memoizedSerializedSize", "mss")
                    line = line.replace("parsedMessage", "pm")
                    line = line.replace("extensionRegistry", "er")
                    line = line.replace("builder", "bd")

                    # Write the new line out to the replacement file
                    g.write(line.encode('utf-8'))

                    # detect package name and add import statements for minification
                    if not pkg and line.startswith("package ") and line.endswith(";\n"):
                        pkg = line[8:-2] + "."
                        g.write(b"import com.google.protobuf.*;\n")
                        g.write(b"import com.google.common.util.concurrent.*;\n")
                        g.write(b"import static com.google.common.util.concurrent.Futures.*;\n")
                        g.write(b"import java.util.*;\n")
                        g.write(b"import java.util.concurrent.*;\n")
                        g.write(b"import java.io.IOException;\n")

                # Commit g over f
                os.rename(g.name, path)

    # Apply several fixups to the generated java files:
    for group in os.walk(output_dir):
        (dirpath, _dirnames, filenames) = group
        for name in filenames:
            filepath = os.path.join(dirpath, name)
            if filepath.endswith('.java'):
                _fixup_gen_java_file(filepath)


def proto_objc(output_dir, includes, protos):
    args_run = [PROTOC, '--objc-arc_out={}'.format(output_dir)]
    args_inc = ['-I{}'.format(include) for include in includes]
    subprocess.check_call(args_run + args_inc + protos)


def proto_python(output_dir, includes, protos):
    args_run = [
        PROTOC,
        '--plugin={}/protobuf-rpc/gen_rpc_python/protoc-gen-rpc-python'.format(
            BUILD_DIR),
        '--python_out={}'.format(output_dir),
        '--rpc-python_out={}'.format(output_dir),
        '-I{}/protobuf.plugins/proto'.format(TOOLS_DIR),
    ]
    args_inc = ['-I{}'.format(include) for include in includes]
    subprocess.check_call(args_run + args_inc + protos)

    # Create a file __init__.py so that the folder in which these python files
    # were generated is treated as a Python module
    with open(os.path.join(output_dir, '__init__.py'), 'wb') as _f:
        pass


LANG_MAPPINGS = {
    "cpp": proto_cpp,
    "java": proto_java,
    "objc": proto_objc,
    "python": proto_python,
}


def clean():
    for folder in PROTO_MAPPINGS.keys():
        if os.path.isdir(folder):
            shutil.rmtree(folder)


def build():
    for folder, opts in PROTO_MAPPINGS.items():
        _mkdir_p(folder)
        if opts['lang'] not in LANG_MAPPINGS:
            raise NotImplementedError(
                'No protobuf backend for {}'.format(opts['lang']))

        LANG_MAPPINGS[opts['lang']](folder, opts['includes'], opts['protos'])


if __name__ == '__main__':
    if len(sys.argv) == 1 or sys.argv[1] == 'build':
        build()
        sys.exit(0)

    if sys.argv[1] == 'clean':
        clean()
        sys.exit(0)
