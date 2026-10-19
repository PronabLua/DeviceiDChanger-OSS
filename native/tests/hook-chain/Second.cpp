#include <dobby.h>

namespace Second {
    int (*Original)(int) = nullptr;

    int Replace(int Value)
    {
        return Original(Value) + 200;
    }
}

extern "C"
{
    __attribute__((visibility("default"))) void *ChainAddress()
    {
        return (void *)Second::Replace;
    }

    __attribute__((visibility("default"))) int ChainInstall(void *Target)
    {
        dobby_disable_near_branch_trampoline();
        dobby_dummy_func_t Backup = nullptr;
        int Result = DobbyHook(Target, (dobby_dummy_func_t)Second::Replace, &Backup);
        Second::Original = (int (*)(int))Backup;
        return Result;
    }

    __attribute__((visibility("default"))) int ChainRemove(void *Target)
    {
        return DobbyDestroy(Target);
    }
}
