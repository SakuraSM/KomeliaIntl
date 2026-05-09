include(ExternalProject)

ExternalProject_Add(ep_ffi
        URL https://github.com/libffi/libffi/releases/download/v3.5.2/libffi-3.5.2.tar.gz
        URL_HASH SHA256=f3a3082a23b37c293a4fcd1053147b371f2ff91fa7ea1b2a52e335676bac82dc
        BUILD_IN_SOURCE 1
        CONFIGURE_COMMAND <SOURCE_DIR>/configure ${HOST_FLAG}
            --disable-exec-static-tramp
            --disable-multi-os-directory
            --disable-static --enable-pax_emutramp
            --prefix ${CMAKE_BINARY_DIR}/sysroot
        BUILD_COMMAND ${Make_EXECUTABLE}
        INSTALL_COMMAND ${Make_EXECUTABLE} install
        USES_TERMINAL_DOWNLOAD true
        USES_TERMINAL_BUILD true
)
