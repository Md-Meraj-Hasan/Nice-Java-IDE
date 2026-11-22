#include <stdbool.h>
#include <stdint.h>
#include <sys/prctl.h>

/*
 * Android Bionic's heap-tagging control is exposed as android_mallopt(),
 * not the POSIX mallopt() declaration in the NDK public malloc.h.
 * The integer option value is BIONIC_M_SET_HEAP_TAGGING_LEVEL (-204);
 * value 0 disables heap pointer tagging before the child JVM allocates.
 */
extern bool android_mallopt(int opcode, void* value, size_t value_size);

__attribute__((constructor))
static void nice_disable_heap_pointer_tagging(void) {
    int tagging_level = 0;
    (void) android_mallopt(-204, &tagging_level, sizeof(tagging_level));
}
