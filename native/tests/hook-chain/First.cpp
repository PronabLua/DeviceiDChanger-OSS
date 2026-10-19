#include <dobby.h>
#include <stdint.h>

#if !defined(__aarch64__)
#error Hook chain test requires Android arm64
#endif

namespace First {
    int (*Original)(int) = nullptr;
    int Mode = 0;

    int Replace(int Value)
    {
        return Original(Value) + 100;
    }

    uintptr_t RejectNear(uint32_t, uintptr_t, size_t)
    {
        return 0;
    }
}

extern "C"
{
    __attribute__((visibility("default"))) void ChainConfigure(int Mode)
    {
        First::Mode = Mode;
        dobby_set_options(Mode != 0, Mode >= 2 ? First::RejectNear : nullptr);
    }

    __attribute__((visibility("default"))) void *ChainAddress()
    {
        return (void *)First::Replace;
    }

    __attribute__((visibility("default"))) int ChainInstall(void *Target)
    {
        void *Backup = nullptr;
        int Result = DobbyHook(Target, (void *)First::Replace, &Backup);
        if (Result != 0)
            return -1;
        First::Original = (int (*)(int))Backup;
        if (First::Mode == 3 && (*(const uint32_t *)Target & 0xfc000000) != 0x14000000)
        {
            if (DobbyDestroy(Target) != 0)
                return -2;
            First::Original = nullptr;
            return 1;
        }
        return 0;
    }

    __attribute__((visibility("default"))) int ChainRemove(void *Target)
    {
        return DobbyDestroy(Target);
    }
}
