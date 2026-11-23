#include <dlfcn.h>
#include <stddef.h>

/*
 * Resolve mallopt dynamically because this app supports Android versions
 * below API 31, while Bionic's heap-tagging option (-204) is available
 * from API 31. The child JVM is launched with this library preloaded, so
 * the constructor runs before HotSpot performs its heap allocations.
 */
typedef int (*MalloptFunction)(int option, int value);

__attribute__((constructor))
static void nice_disable_heap_pointer_tagging(void) {
    void* libc = dlopen("libc.so", RTLD_NOW | RTLD_LOCAL);
    if (libc == NULL) {
        return;
    }

    MalloptFunction mallopt_fn =
            (MalloptFunction) dlsym(libc, "mallopt");
    if (mallopt_fn != NULL) {
        (void) mallopt_fn(-204, 0);
    }

    dlclose(libc);
}
