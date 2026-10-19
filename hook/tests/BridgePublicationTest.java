package io.github.deviceidchanger.hook;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class BridgePublicationTest
{
    private static Bridge ObservedBridge;

    public static void main(String[] Arguments) throws Throwable
    {
        WaitForPublication();
        InvokeOutsideMonitor();
        SameThreadReentryDoesNotWait();
        PreserveReadBehavior();
        RequireSecureCache();
        PreserveCacheIdentity();
        PreserveDelegationArguments();
        PreserveOriginalException();
        System.out.println("PASS: 8 Bridge publication, monitor, cache identity, delegation, and exception checks.");
    }

    private static Bridge CreateBridge(Cache SecureCache)
    {
        return new Bridge("0123456789abcdef", "fedcba9876543210", "com.example.probe", SecureCache);
    }

    private static Method Backup(String Name) throws NoSuchMethodException
    {
        return Cache.class.getMethod(Name, Object.class, String.class, int.class);
    }

    private static void Check(boolean Condition, String Message)
    {
        if (!Condition)
            throw new AssertionError(Message);
    }

    private static void WaitForPublication() throws Throwable
    {
        Cache SecureCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        CountDownLatch Started = new CountDownLatch(1);
        CountDownLatch Finished = new CountDownLatch(1);
        AtomicReference<Object> Result = new AtomicReference<>();
        AtomicReference<Throwable> Failure = new AtomicReference<>();
        Thread Reader = new Thread(() ->
        {
            Started.countDown();
            try
            {
                Result.set(Callback.OnRead(new Object[] {SecureCache, null, "unrelated", 0}));
            }
            catch (Throwable Error)
            {
                Failure.set(Error);
            }
            finally
            {
                Finished.countDown();
            }
        }, "BridgePublicationReader");
        Reader.setDaemon(true);
        try
        {
            synchronized (Callback)
            {
                Reader.start();
                Check(Started.await(2, TimeUnit.SECONDS), "Reader did not start");
                long Deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (Reader.getState() != Thread.State.BLOCKED && Finished.getCount() != 0 && System.nanoTime() < Deadline)
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                Check(Reader.getState() == Thread.State.BLOCKED, "Callback did not wait for backup publication");
                Check(Finished.getCount() != 0, "Callback completed before backup publication");
                Callback.Backup = Backup("ReadOriginal");
            }
            Check(Finished.await(2, TimeUnit.SECONDS), "Callback did not resume after publication");
            Check(Failure.get() == null, "Callback failed after publication");
            Check("original:unrelated:0".equals(Result.get()), "Callback did not delegate to the published backup");
        }
        finally
        {
            Reader.interrupt();
            Reader.join(2000);
        }
    }

    private static void InvokeOutsideMonitor() throws Throwable
    {
        Cache SecureCache = new Cache();
        ObservedBridge = CreateBridge(SecureCache);
        ObservedBridge.Backup = Backup("ReadUnlocked");
        Check("delegated".equals(ObservedBridge.OnRead(new Object[] {SecureCache, null, "unrelated", 0})), "Delegation result changed");
    }

    private static void SameThreadReentryDoesNotWait() throws Throwable
    {
        Cache SecureCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        CountDownLatch Finished = new CountDownLatch(1);
        AtomicReference<Throwable> Failure = new AtomicReference<>();
        Thread Reader = new Thread(() ->
        {
            try
            {
                synchronized (Callback)
                {
                    boolean Rejected = false;
                    try
                    {
                        Callback.OnRead(new Object[] {SecureCache, null, "unrelated", 0});
                    }
                    catch (IllegalStateException Expected)
                    {
                        Rejected = true;
                    }
                    Check(Rejected, "Unpublished same-thread callback must fail explicitly");
                    Callback.Backup = Backup("ReadOriginal");
                    Check("original:unrelated:0".equals(Callback.OnRead(new Object[] {SecureCache, null, "unrelated", 0})), "Published same-thread callback failed");
                }
            }
            catch (Throwable Error)
            {
                Failure.set(Error);
            }
            finally
            {
                Finished.countDown();
            }
        }, "BridgePublicationReentry");
        Reader.setDaemon(true);
        Reader.start();
        try
        {
            Check(Finished.await(2, TimeUnit.SECONDS), "Same-thread reentry waited for its own publication");
            Check(Failure.get() == null, "Same-thread reentry check failed");
        }
        finally
        {
            Reader.interrupt();
            Reader.join(2000);
        }
    }

    private static void PreserveReadBehavior() throws Throwable
    {
        Cache SecureCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        Callback.Backup = Backup("ReadOriginal");
        Check("0123456789abcdef".equals(Callback.OnRead(new Object[] {SecureCache, null, "android_id", 0})), "Selected user ID override changed");
        Check(SecureCache.Calls == 0, "Eligible override called the original cache");
        Check("original:android_id:1".equals(Callback.OnRead(new Object[] {SecureCache, null, "android_id", 1})), "Another user read did not delegate");
        Check("original:accessibility_enabled:0".equals(Callback.OnRead(new Object[] {SecureCache, null, "accessibility_enabled", 0})), "Unrelated setting did not delegate");
    }

    private static void RequireSecureCache()
    {
        boolean Rejected = false;
        try
        {
            CreateBridge(null);
        }
        catch (IllegalArgumentException Expected)
        {
            Rejected = true;
        }
        Check(Rejected, "A missing Secure cache must not become a wildcard receiver");
    }

    private static void PreserveCacheIdentity() throws Throwable
    {
        Cache SecureCache = new Cache();
        Cache OtherCache = new Cache();
        Check(SecureCache != OtherCache && SecureCache.equals(OtherCache), "Equality fixture must contain distinct but equal caches");
        Bridge Callback = CreateBridge(SecureCache);
        Callback.Backup = Backup("ReadOriginal");
        Check("original:android_id:0".equals(Callback.OnRead(new Object[] {OtherCache, null, "android_id", 0})), "Another settings cache received the Secure override");
        Check(OtherCache.Calls == 1 && SecureCache.Calls == 0, "Delegation used the captured cache instead of the actual receiver");
        Check("0123456789abcdef".equals(Callback.OnRead(new Object[] {SecureCache, null, "android_id", 0})), "Exact Secure cache did not receive the override");
    }

    private static void PreserveDelegationArguments() throws Throwable
    {
        Cache SecureCache = new Cache();
        Cache OtherCache = new Cache();
        Object Resolver = new Object();
        String Name = new String("unrelated");
        Bridge Callback = CreateBridge(SecureCache);
        Callback.Backup = Backup("ReadOriginal");
        Object[] Arguments = new Object[] {OtherCache, Resolver, Name, 7};
        Check("original:unrelated:7".equals(Callback.OnRead(Arguments)), "Delegated result changed");
        Check(OtherCache.LastResolver == Resolver && OtherCache.LastName == Name && OtherCache.LastUser == 7, "Delegation changed the resolver, name, or user");
        Check(OtherCache.Calls == 1 && SecureCache.Calls == 0, "Delegation changed receiver identity");
        Check(Arguments[0] == OtherCache && Arguments[1] == Resolver && Arguments[2] == Name && Integer.valueOf(7).equals(Arguments[3]), "Delegation mutated callback arguments");
    }

    private static void PreserveOriginalException() throws Throwable
    {
        Cache SecureCache = new Cache();
        Bridge Callback = CreateBridge(SecureCache);
        Callback.Backup = Backup("ThrowOriginal");
        boolean Preserved = false;
        try
        {
            Callback.OnRead(new Object[] {SecureCache, null, "unrelated", 0});
        }
        catch (IllegalArgumentException Expected)
        {
            Preserved = Expected == SecureCache.OriginalFailure;
        }
        Check(Preserved, "Original exception was not preserved");
    }

    public static final class Cache
    {
        public int Calls;
        public Object LastResolver;
        public String LastName;
        public int LastUser;
        public final IllegalArgumentException OriginalFailure = new IllegalArgumentException("original exception");

        public String ReadOriginal(Object Resolver, String Name, int User)
        {
            Calls++;
            LastResolver = Resolver;
            LastName = Name;
            LastUser = User;
            return "original:" + Name + ":" + User;
        }

        public String ReadUnlocked(Object Resolver, String Name, int User)
        {
            Check(!Thread.holdsLock(ObservedBridge), "Original settings call ran while holding the publication monitor");
            return "delegated";
        }

        public String ThrowOriginal(Object Resolver, String Name, int User)
        {
            throw OriginalFailure;
        }

        @Override
        public boolean equals(Object Other)
        {
            return Other instanceof Cache;
        }

        @Override
        public int hashCode()
        {
            return 1;
        }
    }
}
