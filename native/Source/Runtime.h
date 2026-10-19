#pragma once

#include "Profile.h"
#include <jni.h>

namespace Runtime {
    bool Start(JNIEnv *Environment, void *Dex, const Profile::Snapshot *Snapshot, const char *Package);
}
