#include <dlfcn.h>
#include <errno.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <unistd.h>

#if !defined(__aarch64__)
#error Hook chain test requires Android arm64
#endif

namespace Chain {
    struct Engine
    {
        void *Handle;
        void (*Configure)(int);
        void *(*Address)();
        int (*Install)(void *);
        int (*Remove)(void *);
    };

    bool Load(const char *Path, bool First, Engine *Result)
    {
        Result->Handle = dlopen(Path, RTLD_NOW | RTLD_LOCAL);
        if (!Result->Handle)
        {
            printf("load_failed=%s\n", dlerror());
            return false;
        }
        Result->Configure = (void (*)(int))dlsym(Result->Handle, "ChainConfigure");
        Result->Address = (void *(*)())dlsym(Result->Handle, "ChainAddress");
        Result->Install = (int (*)(void *))dlsym(Result->Handle, "ChainInstall");
        Result->Remove = (int (*)(void *))dlsym(Result->Handle, "ChainRemove");
        return (!First || Result->Configure) && Result->Address && Result->Install && Result->Remove;
    }

    void SignalExit(int Signal)
    {
        _exit(128 + Signal);
    }

    bool CheckValues(int (*Call)(int), int Added)
    {
        return Call(0) == 7 + Added && Call(5) == 12 + Added && Call(-3) == 4 + Added;
    }

    void *MapTarget(size_t PageSize, uintptr_t First, uintptr_t Second)
    {
        uintptr_t Candidates[] = { 0x100000000, 0x200000000, 0x400000000, 0x1000000000 };
        for (uintptr_t Address : Candidates)
        {
            uintptr_t FirstDistance = Address > First ? Address - First : First - Address;
            uintptr_t SecondDistance = Address > Second ? Address - Second : Second - Address;
            if (FirstDistance < 0x100000000 || SecondDistance < 0x100000000)
                continue;
            void *Mapping = mmap((void *)Address, PageSize, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
            if (Mapping != MAP_FAILED)
                return Mapping;
        }
        return nullptr;
    }

    int RunChild(const char *Directory, int Mode)
    {
        signal(SIGALRM, SignalExit);
        signal(SIGSEGV, SignalExit);
        signal(SIGILL, SignalExit);
        signal(SIGBUS, SignalExit);
        alarm(3);
        Engine First = {};
        Engine Second = {};
        char Path[4096] = {};
        if (snprintf(Path, sizeof(Path), "%s/libchain_first.so", Directory) >= (int)sizeof(Path) || !Load(Path, true, &First))
            return 10;
        if (snprintf(Path, sizeof(Path), "%s/libchain_second.so", Directory) >= (int)sizeof(Path) || !Load(Path, false, &Second))
            return 11;
        long PageSize = sysconf(_SC_PAGESIZE);
        if (PageSize <= 0)
            return 12;
        void *Target = MapTarget((size_t)PageSize, (uintptr_t)First.Address(), (uintptr_t)Second.Address());
        if (!Target)
            return 13;
        uint32_t Original[] = { 0x11000400, 0x11000800, 0x11000c00, 0x11000400, 0xd65f03c0, 0xd503201f, 0xd503201f, 0xd503201f };
        memcpy(Target, Original, sizeof(Original));
        __builtin___clear_cache((char *)Target, (char *)Target + sizeof(Original));
        if (mprotect(Target, (size_t)PageSize, PROT_READ | PROT_EXEC) != 0)
            return 14;
        int (*Call)(int) = (int (*)(int))Target;
        if (!CheckValues(Call, 0))
            return 15;
        bool Reverse = Mode == 4 || Mode == 7;
        bool Literal = Mode == 6 || Mode == 7;
        bool Reject = Mode >= 8 && Mode <= 10;
        if (Literal)
        {
            if (mprotect(Target, (size_t)PageSize, PROT_READ | PROT_WRITE) != 0)
                return 22;
            uint32_t Leaf[] = { 0x11000400, 0x11000800, 0x11000c00, 0x11000400, 0xd65f03c0 };
            memcpy((char *)Target + 64, Leaf, sizeof(Leaf));
            Original[0] = 0x58000051;
            Original[1] = 0xd61f0220;
            uintptr_t LeafAddress = (uintptr_t)Target + 64;
            memcpy(Original + 2, &LeafAddress, sizeof(LeafAddress));
            memcpy(Target, Original, sizeof(Original));
            __builtin___clear_cache((char *)Target, (char *)Target + 64 + sizeof(Leaf));
            if (mprotect(Target, (size_t)PageSize, PROT_READ | PROT_EXEC) != 0 || !CheckValues(Call, 0))
                return 23;
        }
        if (Reject)
        {
            if (mprotect(Target, (size_t)PageSize, PROT_READ | PROT_WRITE) != 0)
                return 22;
            if (Mode == 8)
            {
                Original[0] = 0x14000002;
                Original[1] = 0xd503201f;
                Original[2] = 0x11001c00;
                Original[3] = 0xd65f03c0;
            }
            else if (Mode == 9)
            {
                Original[0] = 0x10000041;
                Original[1] = 0x11001c00;
                Original[2] = 0xd65f03c0;
                Original[3] = 0xd503201f;
            }
            else
            {
                Original[0] = 0x18000061;
                Original[1] = 0x0b010000;
                Original[2] = 0xd65f03c0;
                Original[3] = 7;
            }
            memcpy(Target, Original, sizeof(Original));
            __builtin___clear_cache((char *)Target, (char *)Target + sizeof(Original));
            if (mprotect(Target, (size_t)PageSize, PROT_READ | PROT_EXEC) != 0 || !CheckValues(Call, 0))
                return 23;
        }
        First.Configure(Mode <= 3 ? Mode : 1);
        if (Reverse && (Second.Install(Target) != 0 || !CheckValues(Call, 200)))
            return 24;
        uint32_t BeforeFirst[8] = {};
        memcpy(BeforeFirst, Target, sizeof(BeforeFirst));
        int Installed = First.Install(Target);
        printf("mode=%d first_install=%d first_word=%08x\n", Mode, Installed, *(uint32_t *)Target);
        if (Reject)
        {
            bool Unchanged = Installed != 0 && memcmp(Target, Original, sizeof(Original)) == 0 && CheckValues(Call, 0);
            printf("mode=%d rejection_preserved_original=%d\n", Mode, Unchanged);
            return Unchanged ? 0 : 27;
        }
        if (Mode == 2)
        {
            bool Unchanged = Installed != 0 && memcmp(Target, Original, sizeof(Original)) == 0 && CheckValues(Call, 0);
            printf("mode=%d near_failure_refused_before_patch=%d\n", Mode, Unchanged);
            return Unchanged ? 0 : 25;
        }
        if (Mode == 3)
        {
            bool Restored = Installed != 0 && memcmp(Target, Original, sizeof(Original)) == 0 && CheckValues(Call, 0);
            printf("mode=%d guarded_fallback_restored=%d\n", Mode, Restored);
            return Restored ? 0 : 16;
        }
        if (Installed != 0)
            return 17;
        if (Mode != 0)
        {
            bool NearOnly = (*(const uint32_t *)Target & 0xfc000000) == 0x14000000 && memcmp((char *)Target + 4, (char *)BeforeFirst + 4, sizeof(BeforeFirst) - 4) == 0;
            printf("mode=%d near_patch_only_four_bytes=%d\n", Mode, NearOnly);
            if (!NearOnly)
                return 28;
        }
        if (!Reverse && !CheckValues(Call, 100))
            return 17;
        if (!Reverse && Second.Install(Target) != 0)
            return 18;
        printf("mode=%d second_word=%08x invoking_chain\n", Mode, *(uint32_t *)Target);
        int Both = Call(0);
        printf("mode=%d chain_value=%d expected=307\n", Mode, Both);
        if (Both != 307 || !CheckValues(Call, 300))
            return 19;
        if (Mode == 5)
        {
            uint32_t Active[8] = {};
            memcpy(Active, Target, sizeof(Active));
            int Removed = First.Remove(Target);
            bool Preserved = Removed != 0 && memcmp(Active, Target, sizeof(Active)) == 0 && CheckValues(Call, 300);
            printf("mode=%d wrong_owner_remove_rejected=%d\n", Mode, Preserved);
            if (!Preserved)
                return 26;
        }
        if (Reverse)
        {
            if (First.Remove(Target) != 0 || !CheckValues(Call, 200) || Second.Remove(Target) != 0)
                return 20;
        }
        else
        {
            if (Second.Remove(Target) != 0 || !CheckValues(Call, 100) || First.Remove(Target) != 0)
                return 20;
        }
        if (!CheckValues(Call, 0) || memcmp(Target, Original, sizeof(Original)) != 0)
            return 21;
        printf("mode=%d full_restore=1\n", Mode);
        return 0;
    }
}

int main(int Count, char **Arguments)
{
    if (Count != 3)
    {
        fprintf(stderr, "Usage: hook_chain_test <library-directory> <case 0..10; see README>\n");
        return 2;
    }
    if (!Arguments[2][0])
        return 2;
    int Mode = 0;
    for (const char *Digit = Arguments[2]; *Digit; Digit++)
    {
        if (*Digit < '0' || *Digit > '9')
            return 2;
        Mode = Mode * 10 + *Digit - '0';
        if (Mode > 10)
            return 2;
    }
    setvbuf(stdout, nullptr, _IONBF, 0);
    pid_t Child = fork();
    if (Child < 0)
        return 3;
    if (Child == 0)
        _exit(Chain::RunChild(Arguments[1], Mode));
    int Status = 0;
    pid_t Result;
    do
    {
        Result = waitpid(Child, &Status, 0);
    }
    while (Result < 0 && errno == EINTR);
    if (Result < 0)
        return 4;
    int Code = WIFEXITED(Status) ? WEXITSTATUS(Status) : 128 + WTERMSIG(Status);
    printf("mode=%d exit_code=%d success=%d\n", Mode, Code, Code == 0);
    return Code == 0 ? 0 : 1;
}
