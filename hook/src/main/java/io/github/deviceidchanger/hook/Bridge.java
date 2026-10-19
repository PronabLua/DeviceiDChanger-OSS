package io.github.deviceidchanger.hook;

import android.os.Process;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class Bridge
{
    public volatile Method Backup;
    private final String Id;
    private final String Revision;
    private final String Package;
    private final Object SecureCache;
    private final AtomicBoolean ReadReported = new AtomicBoolean(false);
    private final AtomicLong CallCount = new AtomicLong();
    private final AtomicLong IdAttemptCount = new AtomicLong();
    private final AtomicLong EligibleCount = new AtomicLong();
    private final AtomicLong IneligibleCount = new AtomicLong();

    public Bridge(String Id, String Revision, String Package, Object SecureCache)
    {
        if (SecureCache == null)
            throw new IllegalArgumentException("Secure settings cache is required");
        this.Id = Id;
        this.Revision = Revision;
        this.Package = Package;
        this.SecureCache = SecureCache;
        Log.i("DeviceIdChanger", "bridge_created package=" + Package + " revision=" + Revision + " pid=" + Process.myPid() + " diagnostics=secure_cache_v1");
    }

    public Object OnRead(Object[] Arguments) throws Throwable
    {
        Method Original;
        synchronized (this)
        {
            Original = Backup;
        }
        if (Original == null)
            throw new IllegalStateException("Original settings method is unavailable");
        if (Arguments == null || Arguments.length != 4)
            throw new IllegalArgumentException("Settings cache call requires a receiver and three arguments");
        long Sequence = CallCount.incrementAndGet();
        Object Receiver = Arguments[0];
        boolean IsSecureCache = Receiver == SecureCache;
        boolean IsId = "android_id".equals(Arguments[2]);
        boolean HasUser = Arguments[3] instanceof Integer;
        int RequestedUser = HasUser ? ((Integer)Arguments[3]).intValue() : Integer.MIN_VALUE;
        int OwnUser = Process.myUid() / 100000;
        boolean Eligible = IsSecureCache && IsId && HasUser && RequestedUser == OwnUser;
        if (IsId)
        {
            long Attempt = IdAttemptCount.incrementAndGet();
            long Sample = Eligible ? EligibleCount.incrementAndGet() : IneligibleCount.incrementAndGet();
            if (Sample <= 2)
                Log.i("DeviceIdChanger", "android_id_attempt package=" + Package + " revision=" + Revision + " pid=" + Process.myPid() + " path=Settings.Secure.NameValueCache attempt=" + Attempt + " requested_user=" + (HasUser ? Integer.toString(RequestedUser) : "unknown") + " user_kind=" + UserKind(HasUser, RequestedUser, OwnUser) + " eligible=" + Eligible + " secure_cache=" + IsSecureCache + " sample=" + Sample + " sample_limit=2 callers=" + Callers());
        }
        if (Sequence <= 1024 && (Sequence & (Sequence - 1)) == 0)
            Log.i("DeviceIdChanger", (Sequence == 1 ? "callback_seen" : "callback_summary") + " package=" + Package + " revision=" + Revision + " pid=" + Process.myPid() + " path=Settings.Secure.NameValueCache callback_sequence=" + Sequence + " android_id_attempts=" + IdAttemptCount.get() + " eligible_attempts=" + EligibleCount.get() + " ineligible_attempts=" + IneligibleCount.get());
        if (Eligible)
        {
            if (ReadReported.compareAndSet(false, true))
                Log.i("DeviceIdChanger", "android_id_read package=" + Package + " revision=" + Revision + " path=Settings.Secure.NameValueCache");
            return Id;
        }
        try
        {
            return Original.invoke(Receiver, new Object[] {Arguments[1], Arguments[2], Arguments[3]});
        }
        catch (InvocationTargetException Failure)
        {
            throw Failure.getCause();
        }
    }

    private static String UserKind(boolean HasUser, int RequestedUser, int OwnUser)
    {
        if (!HasUser)
            return "invalid";
        if (RequestedUser == OwnUser)
            return "self";
        if (RequestedUser == -2)
            return "current";
        if (RequestedUser == -3)
            return "current_or_self";
        return "other";
    }

    private static String Callers()
    {
        StringBuilder Result = new StringBuilder();
        int Count = 0;
        for (StackTraceElement Frame : Thread.currentThread().getStackTrace())
        {
            String ClassName = Frame.getClassName();
            if (ClassName.equals(Bridge.class.getName()) || ClassName.equals("java.lang.Thread") || ClassName.equals("java.lang.reflect.Method") || ClassName.startsWith("DeviceIdHook"))
                continue;
            if (Count > 0)
                Result.append(',');
            Result.append(FrameToken(ClassName, 160)).append('#').append(FrameToken(Frame.getMethodName(), 80));
            if (++Count == 4)
                break;
        }
        return Count == 0 ? "unavailable" : Result.toString();
    }

    private static String FrameToken(String Value, int Limit)
    {
        StringBuilder Result = new StringBuilder();
        for (int Index = 0; Index < Value.length() && Index < Limit; Index++)
        {
            char Letter = Value.charAt(Index);
            boolean Allowed = Letter >= 'a' && Letter <= 'z' || Letter >= 'A' && Letter <= 'Z' || Letter >= '0' && Letter <= '9' || Letter == '.' || Letter == '$' || Letter == '_';
            Result.append(Allowed ? Letter : '_');
        }
        return Result.toString();
    }
}
