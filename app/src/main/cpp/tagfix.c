#include <malloc.h>

/*
 * Loaded into the child JVM process through LD_PRELOAD.
 * Android 12 / API 31 Bionic supports mallopt option -204 to disable
 * software heap pointer tagging before HotSpot starts allocating memory.
 */
__attribute__((constructor))
static void nice_disable_heap_pointer_tagging(void) {
    (void) mallopt(-204, 0);
}
