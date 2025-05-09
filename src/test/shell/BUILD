load("@rules_python//python:defs.bzl", "py_test")
load("@rules_shell//shell:sh_library.bzl", "sh_library")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

package(default_visibility = ["//visibility:private"])

exports_files([
    "bin/bazel",
    "bin/bazel_jdk_minimal",
    "bin/bazel_nojdk",
    "integration_test_setup.sh",
    "sandboxing_test_utils.sh",
    "unittest.bash",
    "shell_utils.sh",
    "testenv.sh",
])

filegroup(
    name = "srcs",
    srcs = glob(["**"]) + [
        "//src/test/shell/bazel:srcs",
        "//src/test/shell/integration:srcs",
    ],
    visibility = ["//src:__pkg__"],
)

sh_library(
    name = "bashunit",
    srcs = [
        "unittest.bash",
        "unittest_utils.sh",
    ],
    data = [
        ":testenv.sh",
        "//src/test/tools/bzlmod:MODULE.bazel.lock",
        "@bazel_tools//tools/bash/runfiles",
    ],
    visibility = ["//visibility:public"],
)

# Test bashunit with python to avoid recursion.
py_test(
    name = "bashunit_test",
    size = "medium",
    srcs = ["unittest_test.py"],
    data = [
        ":bashunit",
        # This test relies on writing shell scripts that use bash runfiles
        # to load the actual copy of unittest.bash being tested.
        "@bazel_tools//tools/bash/runfiles",
    ],
    main = "unittest_test.py",
    tags = [
        "no_windows",  # test runs bash scripts in a subprocess
    ],
)

sh_library(
    name = "shell_utils",
    srcs = ["shell_utils.sh"],
    visibility = ["//src/test/shell/integration:__pkg__"],
)

sh_test(
    name = "shell_utils_test",
    srcs = ["shell_utils_test.sh"],
    data = [
        ":bashunit",
        ":shell_utils",
        ":testenv.sh",
        "@bazel_tools//tools/bash/runfiles",
    ],
)

sh_test(
    name = "shell_utils_symlinks_test",
    srcs = ["shell_utils_symlinks_test.sh"],
    data = [
        ":bashunit",
        ":shell_utils",
        ":testenv.sh",
        "@bazel_tools//tools/bash/runfiles",
    ],
    tags = [
        "no_windows",  # test asserts symlink behavior
    ],
)
