package com.dips.adblocktest;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Diagnostic-only activity, runs in the separate :crash process so it
 * survives the main process dying. Shows the captured startup crash's
 * stack trace on screen.
 */
public class CrashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String trace = getIntent().getStringExtra("trace");
        if (trace == null) trace = readFromFile();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("AdBlock Test crashed on startup. Show this to boo:");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(24, 24, 24, 12);
        root.addView(title);

        TextView tv = new TextView(this);
        tv.setText(trace != null ? trace : "(no crash trace captured)");
        tv.setTextColor(Color.GREEN);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        tv.setPadding(24, 12, 24, 24);

        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        setContentView(root);
    }

    private String readFromFile() {
        try {
            File f = new File(getFilesDir(), "last_crash.txt");
            if (!f.exists()) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
