load("@rules_cc//cc:objc_library.bzl", "objc_library")
load("@rules_shell//shell:sh_binary.bzl", "sh_binary")

package(default_visibility = ["//visibility:public"])

# Files which shouldn't be publicly visible and dependencies of all objc_* or ios_* rules shouldn't be in this package.
exports_files(glob(["**"]))

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)

sh_binary(
    name = "xcrunwrapper",
    srcs = [":xcrunwrapper.sh"],
)

filegroup(
    name = "gcov",
    srcs = [":gcov_stub"],
)

filegroup(
    name = "mcov",
    srcs = [":mcov_stub"],
)

xcode_config(
    name = "host_xcodes",
)

objc_library(
    name = "dummy_lib",
    srcs = [
        "objc_dummy.mm",
    ],
    alwayslink = False,
)
