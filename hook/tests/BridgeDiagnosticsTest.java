package io.github.deviceidchanger.hook;

import android.util.Log;
import java.util.List;

public final class BridgeDiagnosticsTest
{
    public static void main(String[] Arguments) throws Throwable
    {
        CreationDoesNotClaimInstallation();
        OtherSettingsRemainPrivateAndBounded();
        IdAttemptsAreClassifiedAndBounded();
        MalformedUserStillDelegates();
        OtherCacheIsDiagnosedWithoutValues();
        System.out.println("PASS: 5 Bridge diagnostic privacy, bounds, cache/user predicates, and behavior checks.");
    }

    private static Bridge CreateBridge(Cache SecureCache) throws NoSuchMethodException
    {
        Bridge Callback = new Bridge("0123456789abcdef", "fedcba9876543210", "com.example.probe", SecureCache);
        Callback.Backup = Cache.class.getMethod("ReadOriginal", Object.class, String.class, int.class);
        return Callback;
    }

    private static void Check(boolean Condition, String Message)
    {
        if (!Condition)
            throw new AssertionError(Message);
    }

    private static int Count(String Event)
    {
        int Result = 0;
        for (String Line : Log.Entries())
        {
            if (Line.startsWith("DeviceIdChanger " + Event + " "))
                Result++;
        }
        return Result;
    }

    private static void CheckPrivate(List<String> Lines)
    {
        for (String Line : Lines)
        {
            Check(!Line.contains("0123456789abcdef"), "Override value entered diagnostic logs");
            Check(!Line.contains("private_setting_name"), "Unrelated setting name entered diagnostic logs");
            Check(!Line.contains("private_resolver"), "Resolver argument entered diagnostic logs");
            Check(!Line.contains("private_cache_receiver"), "Cache receiver entered diagnostic logs");
            Check(!Line.contains("private_original_value"), "Original return value entered diagnostic logs");
            Check(!Line.contains("sensitive_user_payload"), "Malformed user argument entered diagnostic logs");
            Check(Line.length() < 1400, "Diagnostic line exceeded its bound");
        }
    }

    private static void CreationDoesNotClaimInstallation() throws Throwable
    {
        Log.Clear();
        CreateBridge(new Cache());
        Check(Log.Entries().size() == 1, "Construction generated extra diagnostic events");
        Check(Count("bridge_created") == 1, "Diagnostic creation marker is missing");
        String Line = Log.Entries().get(0);
        Check(Line.contains("diagnostics=secure_cache_v1"), "Diagnostic version is missing");
        Check(!Line.contains("hook_ready") && !Line.contains("android_id_read"), "Construction claimed a hook or read");
        CheckPrivate(Log.Entries());
    }

    private static void OtherSettingsRemainPrivateAndBounded() throws Throwable
    {
        Log.Clear();
        Cache SecureCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        for (int Index = 0; Index < 2048; Index++)
            Check("private_original_value".equals(Callback.OnRead(new Object[] {SecureCache, "private_resolver", "private_setting_name", 0})), "Other setting result changed");
        Check(Count("callback_seen") == 1 && Count("callback_summary") == 10, "Callback summaries are not bounded at powers of two through 1024");
        Check(Count("android_id_attempt") == 0 && Count("android_id_read") == 0, "Other settings were classified as Android ID reads");
        Check(Log.Entries().size() == 12, "Other setting logging exceeded its event bound");
        CheckPrivate(Log.Entries());
    }

    private static void IdAttemptsAreClassifiedAndBounded() throws Throwable
    {
        Log.Clear();
        Cache SecureCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        Check("private_original_value".equals(Callback.OnRead(new Object[] {SecureCache, "private_resolver", "android_id", -2})), "USER_CURRENT must delegate");
        Check("private_original_value".equals(Callback.OnRead(new Object[] {SecureCache, "private_resolver", "android_id", -3})), "USER_CURRENT_OR_SELF must delegate");
        for (int Index = 0; Index < 2048; Index++)
        {
            Check("0123456789abcdef".equals(Callback.OnRead(new Object[] {SecureCache, "private_resolver", "android_id", 0})), "Self-user override changed");
            Check("private_original_value".equals(Callback.OnRead(new Object[] {SecureCache, "private_resolver", "android_id", 1})), "Other-user delegation changed");
        }
        Check(Count("android_id_attempt") == 4, "Android ID samples exceeded two per eligibility category");
        Check(Count("android_id_read") == 1, "Existing first eligible read event changed");
        boolean CurrentSeen = false;
        boolean CurrentOrSelfSeen = false;
        boolean EligibleSeen = false;
        for (String Line : Log.Entries())
        {
            CurrentSeen |= Line.contains("requested_user=-2 user_kind=current eligible=false");
            CurrentOrSelfSeen |= Line.contains("requested_user=-3 user_kind=current_or_self eligible=false");
            EligibleSeen |= Line.contains("requested_user=0 user_kind=self eligible=true");
            if (Line.startsWith("DeviceIdChanger android_id_attempt "))
                Check(Line.contains("BridgeDiagnosticsTest#IdAttemptsAreClassifiedAndBounded"), "Bounded caller metadata is missing");
        }
        Check(CurrentSeen && CurrentOrSelfSeen && EligibleSeen, "Android user classification is incomplete");
        Check(Log.Entries().size() == 17, "Diagnostic events exceeded the per-instance bound");
        CheckPrivate(Log.Entries());
    }

    private static void MalformedUserStillDelegates() throws Throwable
    {
        Log.Clear();
        Cache SecureCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        boolean RejectedByOriginal = false;
        try
        {
            Callback.OnRead(new Object[] {SecureCache, "private_resolver", "android_id", "sensitive_user_payload"});
        }
        catch (IllegalArgumentException Expected)
        {
            RejectedByOriginal = true;
        }
        Check(RejectedByOriginal, "Malformed user was intercepted instead of delegated");
        Check(Count("android_id_read") == 0, "Malformed user was reported as an override");
        boolean InvalidSeen = false;
        for (String Line : Log.Entries())
            InvalidSeen |= Line.contains("requested_user=unknown user_kind=invalid eligible=false");
        Check(InvalidSeen, "Malformed user was not diagnosed safely");
        CheckPrivate(Log.Entries());
    }

    private static void OtherCacheIsDiagnosedWithoutValues() throws Throwable
    {
        Log.Clear();
        Cache SecureCache = new Cache();
        Cache OtherCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        Check("private_original_value".equals(Callback.OnRead(new Object[] {OtherCache, "private_resolver", "android_id", 0})), "Another cache received an override");
        Check(Count("android_id_attempt") == 1 && Count("android_id_read") == 0, "Another cache was reported as an overridden read");
        boolean OtherCacheSeen = false;
        for (String Line : Log.Entries())
            OtherCacheSeen |= Line.contains("path=Settings.Secure.NameValueCache") && Line.contains("eligible=false secure_cache=false");
        Check(OtherCacheSeen, "Cache identity predicate was not diagnosed");
        CheckPrivate(Log.Entries());
    }

    public static final class Cache
    {
        public String ReadOriginal(Object Resolver, String Name, int User)
        {
            return "private_original_value";
        }

        @Override
        public String toString()
        {
            return "private_cache_receiver";
        }
    }
}
