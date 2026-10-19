#pragma once

#include <stddef.h>
#include <stdint.h>

namespace Profile
{
    constexpr size_t PackageSize = 256;
    constexpr size_t ValueSize = 17;
    constexpr uint32_t Protocol = 0x44494331;
    constexpr uint32_t MinDexSize = 112;
    constexpr uint32_t MaxDexSize = 1048576;

    enum class Status : uint32_t
    {
        Ready,
        Missing,
        Invalid,
        DexMissing
    };

    struct Request
    {
        uint32_t Magic;
        uint32_t Uid;
        char Package[PackageSize];
    };

    struct Snapshot
    {
        uint32_t Magic;
        Status State;
        uint32_t DexSize;
        char Id[ValueSize];
        char Revision[ValueSize];
    };

    bool CheckPackage(const char *Package);
    bool CheckHex(const char *Value);
    bool Parse(const char *Text, size_t Length, Snapshot *Result);
    bool ReadAll(int Descriptor, void *Buffer, size_t Length);
    bool WriteAll(int Descriptor, const void *Buffer, size_t Length);
    void Serve(int Socket);
}
