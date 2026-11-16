# Source this from a shell in the development container.
# It restores the tools that survive /workspace resets, without apk/network.
export PATH=/workspace/Android/tools/bin:$PATH
export HOST_CC=/workspace/Android/tools/bin/hostcc
# adb/fastboot: persistent platform-tools + bundled musl libs
# python3/debugfs/e2fsck/dumpe2fs/git: persistent Debian rootfs (/workspace/sistema)
# hostcc: compiles x86_64 host self-tests inside that chroot
# lpdump: fallback for the fixed Elyvaen base layout
