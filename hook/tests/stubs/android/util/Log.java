package android.util;

import java.util.ArrayList;
import java.util.List;

public final class Log
{
    private static final List<String> Lines = new ArrayList<>();

    public static synchronized int i(String Tag, String Message)
    {
        Lines.add(Tag + " " + Message);
        return 0;
    }

    public static synchronized void Clear()
    {
        Lines.clear();
    }

    public static synchronized List<String> Entries()
    {
        return new ArrayList<>(Lines);
    }
}
