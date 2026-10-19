#include "Profile.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>

namespace Profile
{
    bool CheckPackage(const char *Package)
    {
        size_t Length = strnlen(Package, PackageSize);
        if (Length == 0 || Length >= PackageSize || Package[Length - 1] == '.')
            return false;

        bool Start = true;
        bool Dot = false;
        for (size_t Index = 0; Index < Length; Index++)
        {
            char Letter = Package[Index];
            bool Alpha = (Letter >= 'a' && Letter <= 'z') || (Letter >= 'A' && Letter <= 'Z');
            bool Digit = Letter >= '0' && Letter <= '9';
            if (Letter == '.')
            {
                if (Start)
                    return false;
                Start = true;
                Dot = true;
                continue;
            }
            if ((!Alpha && Letter != '_' && (!Digit || Start)))
                return false;
            Start = false;
        }
        return Dot && strcmp(Package, "io.github.pronablua.deviceidchanger") != 0;
    }

    bool CheckHex(const char *Value)
    {
        if (strnlen(Value, ValueSize) != ValueSize - 1)
            return false;
        for (size_t Index = 0; Index < ValueSize - 1; Index++)
        {
            char Letter = Value[Index];
            if (!((Letter >= '0' && Letter <= '9') || (Letter >= 'a' && Letter <= 'f')))
                return false;
        }
        return true;
    }

    bool Parse(const char *Text, size_t Length, Snapshot *Result)
    {
        if ((Length != 33 && Length != 34) || Text[16] != '\n' || (Length == 34 && Text[33] != '\n'))
            return false;
        memcpy(Result->Id, Text, 16);
        memcpy(Result->Revision, Text + 17, 16);
        Result->Id[16] = '\0';
        Result->Revision[16] = '\0';
        return CheckHex(Result->Id) && CheckHex(Result->Revision);
    }

    bool ReadAll(int Descriptor, void *Buffer, size_t Length)
    {
        size_t Offset = 0;
        while (Offset < Length)
        {
            ssize_t Count = read(Descriptor, (char *)Buffer + Offset, Length - Offset);
            if (Count < 0 && errno == EINTR)
                continue;
            if (Count <= 0)
                return false;
            Offset += (size_t)Count;
        }
        return true;
    }

    bool WriteAll(int Descriptor, const void *Buffer, size_t Length)
    {
        size_t Offset = 0;
        while (Offset < Length)
        {
            ssize_t Count = send(Descriptor, (const char *)Buffer + Offset, Length - Offset, MSG_NOSIGNAL);
            if (Count < 0 && errno == EINTR)
                continue;
            if (Count <= 0)
                return false;
            Offset += (size_t)Count;
        }
        return true;
    }

    int OpenDirectory(int Parent, const char *Name)
    {
        int Descriptor = openat(Parent, Name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
        struct stat Details = {};
        if (Descriptor < 0)
            return -1;
        if (fstat(Descriptor, &Details) != 0 || Details.st_uid != 0 || (Details.st_mode & 0077) != 0)
        {
            close(Descriptor);
            return -1;
        }
        return Descriptor;
    }

    Status ReadProfile(const Request *Query, Snapshot *Reply)
    {
        int Directory = OpenDirectory(AT_FDCWD, "/data/adb/deviceidchanger");
        if (Directory < 0)
            return Status::Missing;
        int Profiles = OpenDirectory(Directory, "profiles");
        close(Directory);
        if (Profiles < 0)
            return Status::Missing;
        char User[16] = {};
        snprintf(User, sizeof(User), "%u", Query->Uid);
        int Apps = OpenDirectory(Profiles, User);
        close(Profiles);
        if (Apps < 0)
            return Status::Missing;
        char Filename[PackageSize + 5] = {};
        snprintf(Filename, sizeof(Filename), "%s.conf", Query->Package);
        int Descriptor = openat(Apps, Filename, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK);
        close(Apps);
        if (Descriptor < 0)
            return errno == ENOENT ? Status::Missing : Status::Invalid;
        struct stat Details = {};
        char Text[35] = {};
        bool Valid = fstat(Descriptor, &Details) == 0 && S_ISREG(Details.st_mode) && Details.st_uid == 0 && (Details.st_mode & 0077) == 0 && (Details.st_size == 33 || Details.st_size == 34);
        if (Valid)
            Valid = ReadAll(Descriptor, Text, (size_t)Details.st_size) && Parse(Text, (size_t)Details.st_size, Reply);
        close(Descriptor);
        return Valid ? Status::Ready : Status::Invalid;
    }

    void *ReadDex(uint32_t *Length)
    {
        int Descriptor = open("/data/adb/modules/deviceidchanger_native/hook.dex", O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK);
        if (Descriptor < 0)
            return nullptr;
        struct stat Details = {};
        bool Valid = fstat(Descriptor, &Details) == 0 && S_ISREG(Details.st_mode) && Details.st_uid == 0 && (Details.st_mode & 0022) == 0 && Details.st_size >= MinDexSize && Details.st_size <= MaxDexSize;
        void *Buffer = Valid ? malloc((size_t)Details.st_size) : nullptr;
        if (Buffer && (!ReadAll(Descriptor, Buffer, (size_t)Details.st_size) || memcmp(Buffer, "dex\n", 4) != 0))
        {
            free(Buffer);
            Buffer = nullptr;
        }
        close(Descriptor);
        if (Buffer)
            *Length = (uint32_t)Details.st_size;
        return Buffer;
    }

    void Serve(int Socket)
    {
        struct timeval Timeout = {2, 0};
        setsockopt(Socket, SOL_SOCKET, SO_RCVTIMEO, &Timeout, sizeof(Timeout));
        setsockopt(Socket, SOL_SOCKET, SO_SNDTIMEO, &Timeout, sizeof(Timeout));
        Request Query = {};
        Snapshot Reply = {};
        Reply.Magic = Protocol;
        Reply.State = Status::Invalid;
        if (!ReadAll(Socket, &Query, sizeof(Query)) || Query.Magic != Reply.Magic || Query.Uid < 10000 || Query.Uid > 19999 || !CheckPackage(Query.Package))
        {
            WriteAll(Socket, &Reply, sizeof(Reply));
            return;
        }
        Reply.State = ReadProfile(&Query, &Reply);
        void *Dex = Reply.State == Status::Ready ? ReadDex(&Reply.DexSize) : nullptr;
        if (Reply.State == Status::Ready && !Dex)
            Reply.State = Status::DexMissing;
        if (WriteAll(Socket, &Reply, sizeof(Reply)) && Dex)
            WriteAll(Socket, Dex, Reply.DexSize);
        free(Dex);
    }
}
