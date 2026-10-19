#include "Runtime.h"

#include <android/api-level.h>
#include <android/log.h>
#include <dobby.h>
#include <lsplant.hpp>
#include <string.h>
#include <string_view>
#include <xdl.h>

extern "C"
{
    void *FindArtPrefix(void *Handle, const char *Prefix);
}

namespace Runtime
{
    void *Art = nullptr;
    jobject Loader = nullptr;
    bool LoadBridge(JNIEnv *Environment, void *Dex, const Profile::Snapshot *Snapshot, const char *Package);

    bool Fail(JNIEnv *Environment, const char *Step)
    {
        if (Environment->ExceptionCheck())
            Environment->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, "DeviceIdChanger", "runtime_failed step=%s", Step);
        return false;
    }

    void *InstallNativeHook(void *Target, void *Replacement)
    {
        void *Original = nullptr;
        if (DobbyHook(Target, Replacement, &Original) != 0)
            return nullptr;
        return Original;
    }

    bool RemoveNativeHook(void *Target)
    {
        return DobbyDestroy(Target) == 0;
    }

    void *FindSymbol(std::string_view Name)
    {
        char Symbol[1024] = {};
        if (Name.size() >= sizeof(Symbol))
            return nullptr;
        memcpy(Symbol, Name.data(), Name.size());
        void *Address = xdl_sym(Art, Symbol, nullptr);
        return Address ? Address : xdl_dsym(Art, Symbol, nullptr);
    }

    void *FindPrefix(std::string_view Name)
    {
        char Symbol[1024] = {};
        if (Name.size() >= sizeof(Symbol))
            return nullptr;
        memcpy(Symbol, Name.data(), Name.size());
        return FindArtPrefix(Art, Symbol);
    }

    bool Start(JNIEnv *Environment, void *Dex, const Profile::Snapshot *Snapshot, const char *Package)
    {
        int Version = android_get_device_api_level();
        if (Version < 29 || Version > 37)
            return Fail(Environment, "unsupported_android_version");
        if (Environment->PushLocalFrame(32) != JNI_OK)
            return Fail(Environment, "local_frame");
        Art = xdl_open("libart.so", XDL_DEFAULT);
        if (!Art)
        {
            Environment->PopLocalFrame(nullptr);
            return Fail(Environment, "open_art");
        }
        lsplant::InitInfo Hooks = {};
        Hooks.inline_hooker = InstallNativeHook;
        Hooks.inline_unhooker = RemoveNativeHook;
        Hooks.art_symbol_resolver = FindSymbol;
        Hooks.art_symbol_prefix_resolver = FindPrefix;
        Hooks.generated_class_name = "DeviceIdHook";
        Hooks.generated_source_name = "DeviceIdChanger";
        Hooks.generated_field_name = "Callback";
        Hooks.generated_method_name = "Read";
        dobby_set_near_trampoline(true);
        if (!lsplant::Init(Environment, Hooks))
        {
            Environment->PopLocalFrame(nullptr);
            return Fail(Environment, "init_art_hook");
        }
        __android_log_print(ANDROID_LOG_INFO, "DeviceIdChanger", "engine_ready engine=arm64_near16_v1");
        bool Ready = LoadBridge(Environment, Dex, Snapshot, Package);
        Environment->PopLocalFrame(nullptr);
        return Ready;
    }

    bool LoadBridge(JNIEnv *Environment, void *Dex, const Profile::Snapshot *Snapshot, const char *Package)
    {
        jclass ClassLoader = Environment->FindClass("java/lang/ClassLoader");
        if (!ClassLoader)
            return Fail(Environment, "class_loader_class");
        jmethodID GetSystem = Environment->GetStaticMethodID(ClassLoader, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
        if (!GetSystem)
            return Fail(Environment, "system_loader_method");
        jobject Parent = Environment->CallStaticObjectMethod(ClassLoader, GetSystem);
        if (!Parent || Environment->ExceptionCheck())
            return Fail(Environment, "system_loader");
        jclass MemoryLoader = Environment->FindClass("dalvik/system/InMemoryDexClassLoader");
        if (!MemoryLoader)
            return Fail(Environment, "memory_loader_class");
        jmethodID Constructor = Environment->GetMethodID(MemoryLoader, "<init>", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
        if (!Constructor)
            return Fail(Environment, "memory_loader_constructor");
        jobject Buffer = Environment->NewDirectByteBuffer(Dex, Snapshot->DexSize);
        if (!Buffer)
            return Fail(Environment, "dex_buffer");
        jobject Instance = Environment->NewObject(MemoryLoader, Constructor, Buffer, Parent);
        if (!Instance || Environment->ExceptionCheck())
            return Fail(Environment, "dex_loader");
        Loader = Environment->NewGlobalRef(Instance);
        if (!Loader)
            return Fail(Environment, "keep_dex_loader");
        jmethodID LoadClass = Environment->GetMethodID(ClassLoader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        if (!LoadClass)
            return Fail(Environment, "load_class_method");
        jstring Name = Environment->NewStringUTF("io.github.deviceidchanger.hook.Bridge");
        if (!Name)
            return Fail(Environment, "bridge_name");
        jclass Bridge = (jclass)Environment->CallObjectMethod(Loader, LoadClass, Name);
        if (!Bridge || Environment->ExceptionCheck())
            return Fail(Environment, "bridge_class");
        jmethodID BridgeConstructor = Environment->GetMethodID(Bridge, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V");
        if (!BridgeConstructor)
            return Fail(Environment, "bridge_constructor");
        jstring Id = Environment->NewStringUTF(Snapshot->Id);
        if (!Id)
            return Fail(Environment, "bridge_id");
        jstring Revision = Environment->NewStringUTF(Snapshot->Revision);
        if (!Revision)
            return Fail(Environment, "bridge_revision");
        jstring TargetPackage = Environment->NewStringUTF(Package);
        if (!TargetPackage)
            return Fail(Environment, "bridge_package");
        jclass Secure = Environment->FindClass("android/provider/Settings$Secure");
        if (!Secure)
            return Fail(Environment, "settings_secure_class");
        jfieldID CacheField = Environment->GetStaticFieldID(Secure, "sNameValueCache", "Landroid/provider/Settings$NameValueCache;");
        if (!CacheField)
            return Fail(Environment, "settings_secure_cache_field");
        jobject SecureCache = Environment->GetStaticObjectField(Secure, CacheField);
        if (!SecureCache || Environment->ExceptionCheck())
            return Fail(Environment, "settings_secure_cache");
        jclass Cache = Environment->FindClass("android/provider/Settings$NameValueCache");
        if (!Cache)
            return Fail(Environment, "settings_cache_class");
        jmethodID Target = Environment->GetMethodID(Cache, "getStringForUser", "(Landroid/content/ContentResolver;Ljava/lang/String;I)Ljava/lang/String;");
        if (!Target)
            return Fail(Environment, "settings_cache_get_string_for_user");
        jobject Callback = Environment->NewObject(Bridge, BridgeConstructor, Id, Revision, TargetPackage, SecureCache);
        if (!Callback || Environment->ExceptionCheck())
            return Fail(Environment, "bridge_instance");
        jmethodID OnRead = Environment->GetMethodID(Bridge, "OnRead", "([Ljava/lang/Object;)Ljava/lang/Object;");
        if (!OnRead)
            return Fail(Environment, "bridge_callback");
        jfieldID BackupField = Environment->GetFieldID(Bridge, "Backup", "Ljava/lang/reflect/Method;");
        if (!BackupField)
            return Fail(Environment, "bridge_backup_field");
        jobject TargetMethod = Environment->ToReflectedMethod(Cache, Target, JNI_FALSE);
        if (!TargetMethod || Environment->ExceptionCheck())
            return Fail(Environment, "reflect_target_method");
        jobject CallbackMethod = Environment->ToReflectedMethod(Bridge, OnRead, JNI_FALSE);
        if (!CallbackMethod || Environment->ExceptionCheck())
            return Fail(Environment, "reflect_callback_method");
        if (Environment->MonitorEnter(Callback) != JNI_OK)
            return Fail(Environment, "lock_callback_publication");
        jobject Backup = lsplant::Hook(Environment, TargetMethod, Callback, CallbackMethod);
        bool Ready = Backup && !Environment->ExceptionCheck();
        if (!Ready)
            Fail(Environment, "install_settings_hook");
        else
        {
            Environment->SetObjectField(Callback, BackupField, Backup);
            if (Environment->ExceptionCheck())
                Ready = Fail(Environment, "set_original_method");
        }
        if (!Ready)
        {
            bool Installed = lsplant::IsHooked(Environment, TargetMethod);
            if (Environment->ExceptionCheck())
                Fail(Environment, "query_failed_settings_hook");
            else if (Installed && !lsplant::UnHook(Environment, TargetMethod))
                Fail(Environment, "rollback_settings_hook");
        }
        if (Environment->ExceptionCheck())
            Ready = Fail(Environment, "cleanup_settings_hook");
        if (Environment->MonitorExit(Callback) != JNI_OK)
            return Fail(Environment, "unlock_callback_publication");
        if (Ready)
            __android_log_print(ANDROID_LOG_INFO, "DeviceIdChanger", "hook_path path=Settings.NameValueCache.getStringForUser receiver=SecureCache");
        return Ready;
    }
}
