#if !defined(__aarch64__)
#error DeviceIdChanger requires Android arm64
#endif

#include "Profile.h"
#include "Runtime.h"
#include <sys/types.h>
#include <zygisk.hpp>

#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

class DeviceIdModule : public zygisk::ModuleBase
{
public:
    void onLoad(zygisk::Api *Interface, JNIEnv *Environment) override
    {
        Api = Interface;
        Java = Environment;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *Arguments) override
    {
        if (Arguments->uid < 10000 || Arguments->uid > 19999 || !Arguments->app_data_dir || (Arguments->is_child_zygote && *Arguments->is_child_zygote))
        {
            Api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        const char *Directory = Java->GetStringUTFChars(Arguments->app_data_dir, nullptr);
        if (!Directory)
        {
            Java->ExceptionClear();
            Api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        const char *Name = nullptr;
        if (strncmp(Directory, "/data/user/0/", 13) == 0)
            Name = Directory + 13;
        else if (strncmp(Directory, "/data/data/", 11) == 0)
            Name = Directory + 11;
        else if (strncmp(Directory, "/data/user_de/0/", 16) == 0)
            Name = Directory + 16;
        if (Name && Profile::CheckPackage(Name))
            memcpy(Package, Name, strlen(Name));
        Java->ReleaseStringUTFChars(Arguments->app_data_dir, Directory);
        if (!Profile::CheckPackage(Package))
        {
            Api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        int Socket = Api->connectCompanion();
        if (Socket < 0)
        {
            Api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        struct timeval Timeout = {2, 0};
        setsockopt(Socket, SOL_SOCKET, SO_RCVTIMEO, &Timeout, sizeof(Timeout));
        setsockopt(Socket, SOL_SOCKET, SO_SNDTIMEO, &Timeout, sizeof(Timeout));
        Profile::Request Query = {};
        Query.Magic = Profile::Protocol;
        Query.Uid = (uint32_t)Arguments->uid;
        memcpy(Query.Package, Package, sizeof(Package));
        bool Valid = Profile::WriteAll(Socket, &Query, sizeof(Query)) && Profile::ReadAll(Socket, &Snapshot, sizeof(Snapshot));
        Valid = Valid && Snapshot.Magic == Query.Magic && Snapshot.State == Profile::Status::Ready && Snapshot.DexSize >= Profile::MinDexSize && Snapshot.DexSize <= Profile::MaxDexSize && Profile::CheckHex(Snapshot.Id) && Profile::CheckHex(Snapshot.Revision);
        if (Valid)
        {
            Dex = malloc(Snapshot.DexSize);
            Valid = Dex && Profile::ReadAll(Socket, Dex, Snapshot.DexSize) && memcmp(Dex, "dex\n", 4) == 0;
        }
        close(Socket);
        if (!Valid)
        {
            if (Snapshot.State > Profile::Status::Missing)
                __android_log_print(ANDROID_LOG_WARN, "DeviceIdChanger", "snapshot_failed package=%s status=%u", Package, (uint32_t)Snapshot.State);
            free(Dex);
            Dex = nullptr;
            Api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override
    {
        if (!Dex)
            return;
        bool Ready = Runtime::Start(Java, Dex, &Snapshot, Package);
        __android_log_print(Ready ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, "DeviceIdChanger", "%s package=%s revision=%s", Ready ? "hook_ready" : "hook_failed", Package, Snapshot.Revision);
    }

    void preServerSpecialize(zygisk::ServerSpecializeArgs *) override
    {
        Api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

private:
    zygisk::Api *Api = nullptr;
    JNIEnv *Java = nullptr;
    Profile::Snapshot Snapshot = {};
    char Package[Profile::PackageSize] = {};
    void *Dex = nullptr;
};

REGISTER_ZYGISK_MODULE(DeviceIdModule)
REGISTER_ZYGISK_COMPANION(Profile::Serve)
