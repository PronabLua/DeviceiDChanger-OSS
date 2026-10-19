package io.github.pronablua.deviceidchanger.probe;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class ProbeActivity extends Activity {
    private final String activityInstance = UUID.randomUUID().toString();
    private int readCounter;
    private TextView observationView;
    private TextView fileView;
    private boolean worker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        worker = Application.getProcessName().endsWith(":worker");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = pixels(16);
        content.setPadding(padding, padding, padding, padding);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = systemBars(insets);
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            return insets;
        });

        TextView heading = new TextView(this);
        heading.setText((getPackageName().endsWith(".control") ? "CONTROL" : "TARGET") + " · " + (worker ? "worker process" : "main process"));
        heading.setTextSize(24);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setAccessibilityHeading(true);
        content.addView(heading, matchWidth());

        TextView explanation = new TextView(this);
        explanation.setText("Each read calls Settings.Secure.getString again. Values are recorded in this app’s private storage.");
        explanation.setTextSize(16);
        explanation.setPadding(0, pixels(12), 0, pixels(12));
        content.addView(explanation, matchWidth());

        observationView = new TextView(this);
        observationView.setTextSize(17);
        observationView.setTypeface(Typeface.MONOSPACE);
        observationView.setTextIsSelectable(true);
        observationView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(observationView, matchWidth());

        Button reread = new Button(this);
        reread.setText("Reread settings");
        reread.setMinHeight(pixels(48));
        reread.setOnClickListener(view -> observe("manualRead"));
        content.addView(reread, matchWidth());

        Button switchProcess = new Button(this);
        switchProcess.setText(worker ? "Open main process" : "Open worker process");
        switchProcess.setMinHeight(pixels(48));
        switchProcess.setOnClickListener(view -> {
            Intent intent = new Intent(this, worker ? MainActivity.class : WorkerActivity.class);
            intent.putExtra("readReason", "processButton");
            startActivity(intent);
        });
        content.addView(switchProcess, matchWidth());

        fileView = new TextView(this);
        fileView.setTextSize(14);
        fileView.setTextIsSelectable(true);
        fileView.setPadding(0, pixels(12), 0, 0);
        content.addView(fileView, matchWidth());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
        content.requestApplyInsets();
        observe("onCreate");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        observe("onNewIntent");
    }

    private void observe(String event) {
        readCounter++;
        String androidId = null;
        String androidIdError = null;
        String accessibilityEnabled = null;
        String accessibilityError = null;
        try {
            androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (RuntimeException failure) {
            androidIdError = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        try {
            accessibilityEnabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (RuntimeException failure) {
            accessibilityError = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        long elapsedRealtime = SystemClock.elapsedRealtime();
        String reason = getIntent().getStringExtra("readReason");
        if (reason != null && reason.length() > 128) reason = reason.substring(0, 128);
        File observations = new File(getFilesDir(), worker ? "observations-worker.jsonl" : "observations-main.jsonl");
        try {
            JSONObject record = new JSONObject();
            record.put("package", getPackageName());
            record.put("process", Application.getProcessName());
            record.put("pid", Process.myPid());
            record.put("uid", Process.myUid());
            record.put("activityInstance", activityInstance);
            record.put("event", event);
            record.put("readReason", reason == null ? JSONObject.NULL : reason);
            record.put("readCounter", readCounter);
            record.put("androidId", androidId == null ? JSONObject.NULL : androidId);
            record.put("androidIdError", androidIdError == null ? JSONObject.NULL : androidIdError);
            record.put("accessibilityEnabled", accessibilityEnabled == null ? JSONObject.NULL : accessibilityEnabled);
            record.put("accessibilityError", accessibilityError == null ? JSONObject.NULL : accessibilityError);
            record.put("processStartedElapsedRealtimeMs", Process.getStartElapsedRealtime());
            record.put("elapsedRealtimeMs", elapsedRealtime);
            record.put("wallTimeMs", System.currentTimeMillis());
            byte[] encoded = (record.toString() + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream output = new FileOutputStream(observations, true)) {
                output.write(encoded);
                output.getFD().sync();
            }
            fileView.setText("Saved: files/" + observations.getName() + "\nPackage: " + getPackageName());
        } catch (JSONException | IOException failure) {
            fileView.setText("Recording failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        observationView.setText(
            "Android ID\n" + show(androidId, androidIdError)
            + "\n\nUnrelated secure setting\naccessibility_enabled = " + show(accessibilityEnabled, accessibilityError)
            + "\n\nPID: " + Process.myPid()
            + "\nUID: " + Process.myUid()
            + "\nActivity read count: " + readCounter
            + "\nElapsed realtime (ms): " + elapsedRealtime
            + "\nEvent: " + event
            + "\nReason: " + (reason == null ? "none" : reason)
            + "\nProcess: " + Application.getProcessName()
        );
    }

    private String show(String value, String error) {
        if (error != null) return "ERROR: " + error;
        return value == null ? "null" : value;
    }

    private int pixels(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private Insets systemBars(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        }
        return insets.getSystemWindowInsets();
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
