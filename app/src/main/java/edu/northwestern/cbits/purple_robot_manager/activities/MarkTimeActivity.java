package edu.northwestern.cbits.purple_robot_manager.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.config.LegacyJSONConfigFile;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.scripting.JavaScriptEngine;

public class MarkTimeActivity extends AppCompatActivity
{
    private static final String SAVED_TIMESTAMPS = "edu.northwestern.cbits.purple_robot_manager.activitiesMarkTimeActivity.SAVED_TIMESTAMPS";
    private static final String SAVED_PROPERTIES = "edu.northwestern.cbits.purple_robot_manager.activitiesMarkTimeActivity.SAVED_PROPERTIES";
    private static final String SELECTED_MODE = "edu.northwestern.cbits.purple_robot_manager.activitiesMarkTimeActivity.SELECTED_MODE";

    private ArrayList<JSONObject> _timestamps = new ArrayList<>();

    private static final int MODE_TIME_POINT = 0;
    private static final int MODE_TIME_RANGE = 1;

    private int _mode = MarkTimeActivity.MODE_TIME_POINT;
    private long _currentStart = 0;
    private boolean _timing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.layout_mark_time_activity);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        this._mode = prefs.getInt(MarkTimeActivity.SELECTED_MODE, MarkTimeActivity.MODE_TIME_POINT);

        this.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        this.updateTitle();

        this.loadTimestamps();
    }

    private void updateTitle() {
        if (this._mode == MarkTimeActivity.MODE_TIME_POINT) {
            this.getSupportActionBar().setTitle(R.string.title_mark_time);
            this.getSupportActionBar().setSubtitle(R.string.mode_mark_time_point);
        } else if (this._mode == MarkTimeActivity.MODE_TIME_RANGE) {
            this.updateDurationTitle();

            this.getSupportActionBar().setSubtitle(R.string.mode_mark_time_range);
        }
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void updateDurationTitle() {
        long duration = 0;

        if (this._currentStart > 0) {
            long now = System.currentTimeMillis();
            duration = now - this._currentStart;
        }
        long milliseconds = duration % 1000;

        long seconds = duration / 1000;

        long minutes = seconds / 60;
        seconds = seconds % 60;

        String title = "";

        if (minutes < 10) {
            title += "0";
        }

        title += "" + minutes + ":";

        if (seconds < 10) {
            title += "0";
        }

        title += "" + seconds + ".";

        if (milliseconds < 100) {
            title += "0";
        }

        if (milliseconds < 10) {
            title += "0";
        }

        title += milliseconds;

        this.getSupportActionBar().setTitle(title);
    }

    private void loadTimestamps()
    {
        final MarkTimeActivity me = this;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        try
        {
            JSONArray saved = new JSONArray(prefs.getString(MarkTimeActivity.SAVED_TIMESTAMPS, "[]"));

            for (int i = 0; i < saved.length(); i++)
            {
                try
                {
                    _timestamps.add(saved.getJSONObject(i));

                    Collections.sort(_timestamps, new Comparator<JSONObject>() {
                        @Override
                        public int compare(JSONObject one, JSONObject two) {
                            try {
                                Long oneTs = one.getLong("t");
                                Long twoTs = two.getLong("t");

                                return oneTs.compareTo(twoTs);
                            } catch (JSONException e) {
                                LogManager.getInstance(me).logException(e);
                            }

                            return 0;
                        }
                    });
                }
                catch (JSONException e)
                {
                    e.printStackTrace();
                }
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        this.setIntent(intent);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onResume()
    {
        super.onResume();

        final MarkTimeActivity me = this;

        ImageView createButton = (ImageView) this.findViewById(R.id.create_button);
        final AutoCompleteTextView name = (AutoCompleteTextView) this.findViewById(R.id.text_label_text);

        createButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                long now = System.currentTimeMillis();

                long[] spec = { 0L, 100L };

                Vibrator vibrator = (Vibrator) me.getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(spec, -1);

                if (me._mode == MarkTimeActivity.MODE_TIME_RANGE) {
                    if (me._currentStart == 0) {
                        me._currentStart = now;

                        me._timing = true;

                        Thread t = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while (me._timing) {
                                    me.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            me.updateTitle();
                                        }
                                    });

                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });

                        t.start();

                        return;
                    }
                }

                me._timing = false;

                String nameValue = name.getText().toString();

                if (nameValue.trim().length() == 0)
                    nameValue = me.getString(R.string.value_anonymous_timestamp);

                JSONObject mark = new JSONObject();

                try {
                    JavaScriptEngine js = new JavaScriptEngine(me);

                    nameValue = LegacyJSONConfigFile.toSlug(nameValue);

                    mark.put("name", nameValue);
                    mark.put("t", now);

                    if (me._mode == MarkTimeActivity.MODE_TIME_RANGE) {
                        mark.put("start", me._currentStart);
                        mark.put("end", now);
                        mark.put("duration", now - me._currentStart);

                        me._currentStart = 0;
                    }

                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);

                    JSONObject props =  new JSONObject(prefs.getString(MarkTimeActivity.SAVED_PROPERTIES, "{}"));

                    Iterator<String> keys = props.keys();

                    while (keys.hasNext()) {
                        String key = keys.next();

                        mark.put(key, props.get(key));

                        if (key.toLowerCase().trim().equals("probe")) {
                            nameValue = LegacyJSONConfigFile.toSlug(props.get(key).toString());
                        }
                    }

                    me._timestamps.add(mark);

                    org.mozilla.javascript.Context jsContext = org.mozilla.javascript.Context.enter();
                    jsContext.setOptimizationLevel(-1);

                    Scriptable scope = jsContext.initStandardObjects();
                    NativeObject payload = (NativeObject) jsContext.evaluateString(scope, "(" + mark.toString() + ")", "<inline>", 1, null);

                    js.emitReading(nameValue, payload);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                me.saveTimestamps();
                me.refreshList();
            }
        });

        this.refreshList();
        this.refreshProperties();
    }

    private void refreshProperties() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            final JSONObject properties =  new JSONObject(prefs.getString(MarkTimeActivity.SAVED_PROPERTIES, "{}"));

            LinearLayout detailsBar = (LinearLayout) this.findViewById(R.id.bar_details);

            ArrayList<View> toRemove = new ArrayList<>();

            for (int i = 0; i < detailsBar.getChildCount(); i++) {
                View child = detailsBar.getChildAt(i);

                if (child instanceof LinearLayout) {
                    toRemove.add(child);
                }
            }

            for (View view : toRemove) {
                detailsBar.removeView(view);
            }

            DisplayMetrics metrics = getResources().getDisplayMetrics();

            final MarkTimeActivity me = this;

            Iterator<String> keys = properties.keys();

            while(keys.hasNext()) {
                String key = keys.next();

                LinearLayout propertyRow = new LinearLayout(this);
                propertyRow.setOrientation(LinearLayout.HORIZONTAL);
                propertyRow.setBackgroundColor(0x10ffffff);

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 0, 0, 16);
                propertyRow.setLayoutParams(rowParams);

                ImageView button = new ImageView(this);
                button.setImageResource(R.drawable.ic_icon_delete_property);
                int buttonPadding = (int) (metrics.density * 8);
                button.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);

                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams((int) (metrics.density * 32), (int) (metrics.density * 32));
                buttonParams.setMargins(0, 0, 16, 0);
                button.setLayoutParams(buttonParams);
                button.setScaleType(ImageView.ScaleType.FIT_CENTER);
                propertyRow.addView(button);

                final String clickKey = key;
                final String clickValue = properties.getString(key);

                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(me);
                        builder.setTitle(R.string.title_clear_property);

                        builder.setMessage(me.getString(R.string.message_confirm_delete_property, clickKey, clickValue));

                        builder.setPositiveButton(R.string.action_clear, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                properties.remove(clickKey);

                                SharedPreferences.Editor e = prefs.edit();
                                e.putString(MarkTimeActivity.SAVED_PROPERTIES, properties.toString());
                                e.apply();

                                me.refreshProperties();
                            }
                        });

                        builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                // Do nothing...
                            }
                        });

                        builder.create().show();
                    }
                });

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, (int) (metrics.density * 32));
                params.weight = 1;

                TextView keyView = new TextView(this);
                keyView.setTypeface(null, Typeface.BOLD);
                keyView.setGravity(Gravity.CENTER_VERTICAL);
                keyView.setText(key);
                keyView.setLayoutParams(params);

                propertyRow.addView(keyView);

                TextView valueView = new TextView(this);
                valueView.setTypeface(null, Typeface.BOLD);
                valueView.setGravity(Gravity.CENTER_VERTICAL);
                valueView.setText(properties.getString(key));
                valueView.setLayoutParams(params);

                propertyRow.addView(valueView);

                detailsBar.addView(propertyRow, 0);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void refreshList()
    {
        final MarkTimeActivity me = this;

        ListView list = (ListView) this.findViewById(R.id.list_timestamps);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        list.setStackFromBottom(true);

        final ArrayList<String> names = new ArrayList<>();

        for (int i = 0; i < this._timestamps.size(); i++)
        {
            try {
                String name = this._timestamps.get(i).getString("name");

                if (names.contains(name) == false)
                    names.add(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        Collections.sort(names);

        final java.text.DateFormat dateFormat = DateFormat.getMediumDateFormat(this);
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(this);

        ArrayAdapter<JSONObject> adapter = new ArrayAdapter<JSONObject>(this, R.layout.layout_timestamp_row, R.id.name_label, this._timestamps)
        {
            @NonNull
            public View getView(int position, View convertView, @NonNull ViewGroup parent)
            {
                convertView = super.getView(position, convertView, parent);

                TextView dateLabel = (TextView) convertView.findViewById(R.id.date_label);
                TextView nameLabel = (TextView) convertView.findViewById(R.id.name_label);

                JSONObject item = me._timestamps.get(position);

                try {
                    long timestamp = item.getLong("t");

                    String name = item.getString("name");

                    Date date = new Date(timestamp);

                    dateLabel.setText(timeFormat.format(date) + " @ " + dateFormat.format(date));
                    nameLabel.setText(name);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                return convertView;
            }
        };

        list.setAdapter(adapter);
        list.setEmptyView(this.findViewById(R.id.placeholder_timestamps));

        final AutoCompleteTextView nameField = (AutoCompleteTextView) this.findViewById(R.id.text_label_text);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                JSONObject item = me._timestamps.get(position);

                try {
                    String name = item.getString("name");

                    nameField.setText(name);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener()
        {
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id)
            {
                JSONObject item = me._timestamps.get(position);

                try {
                    long timestamp = item.getLong("t");
                    String name = item.getString("name");

                    Date date = new Date(timestamp);

                    String dateString = timeFormat.format(date) + " @ " + dateFormat.format(date);

                    AlertDialog.Builder builder = new AlertDialog.Builder(me);
                    builder.setTitle(R.string.title_clear_timestamp);
                    builder.setMessage(me.getString(R.string.message_clear_timestamp, name, dateString));

                    builder.setPositiveButton(R.string.action_clear, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            me._timestamps.remove(position);
                            me.saveTimestamps();

                            me.refreshList();
                        }
                    });

                    builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // Do nothing...
                        }
                    });

                    builder.create().show();


                } catch (JSONException e) {
                    e.printStackTrace();
                }

                return true;
            }
        });

        final AutoCompleteTextView name = (AutoCompleteTextView) this.findViewById(R.id.text_label_text);

        ArrayAdapter<String> namesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names);
        name.setAdapter(namesAdapter);
        name.setThreshold(1);
    }

    private void saveTimestamps()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        JSONArray saved = new JSONArray();

        for (int i = 0; i < this._timestamps.size(); i++)
        {
            saved.put(this._timestamps.get(i));
        }

        SharedPreferences.Editor e = prefs.edit();
        e.putString(MarkTimeActivity.SAVED_TIMESTAMPS, saved.toString());
        e.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.menu_mark_time_activity, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        final MarkTimeActivity me = this;

        final int itemId = item.getItemId();

        if (itemId == android.R.id.home)
        {
            this.finish();
        }
        else if (itemId == R.id.menu_mail)
        {
            StringBuilder message = new StringBuilder();
            String newline = System.getProperty("line.separator");

            ArrayList<String> columns = new ArrayList<>();
            columns.add("name");
            columns.add("t");

            for (int i = 0; i < this._timestamps.size(); i++) {
                JSONObject record = this._timestamps.get(i);

                Iterator<String> keys = record.keys();

                while (keys.hasNext()) {
                    String key = keys.next();

                    if (columns.contains(key) == false) {
                        columns.add(key);
                    }
                }
            }

            String header = "";

            for (String column : columns) {
                if (header.length() > 0) {
                    header += "\t";
                }

                if ("name".equals(column)) {
                    header += "Time Point Name";
                } else if ("t".equals(column)) {
                    header += "Timestamp";
                } else if ("start".equals(column)) {
                    header += "Start";
                } else if ("end".equals(column)) {
                    header += "End";
                } else if ("duration".equals(column)) {
                    header += "Duration";
                } else {
                    header += column;
                }
            }

            message.append(header);
            message.append(newline);

            for (int i = 0; i < this._timestamps.size(); i++) {
                JSONObject record = this._timestamps.get(i);

                try {
                    String line = "";

                    for (String column : columns) {
                        if (line.length() > 0) {
                            line += "\t";
                        }

                        if (record.has(column)) {
                            line += record.get(column).toString();
                        }
                    }

                    message.append(line);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                message.append(newline);
            }

            try
            {
                Intent intent = new Intent(Intent.ACTION_SEND);

                intent.setType("message/rfc822");
                intent.putExtra(Intent.EXTRA_SUBJECT, this.getString(R.string.email_timetamp_subject));
                intent.putExtra(Intent.EXTRA_TEXT, this.getString(R.string.email_timetamp_message));

                File cacheDir = this.getExternalCacheDir();
                File configFile = new File(cacheDir, "time-points.txt");

                FileOutputStream fout = new FileOutputStream(configFile);

                fout.write(message.toString().getBytes(Charset.defaultCharset().name()));

                fout.flush();
                fout.close();

                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(configFile));

                this.startActivity(intent);
            }
            catch (ActivityNotFoundException e)
            {
                Toast.makeText(this, R.string.toast_mail_not_found, Toast.LENGTH_LONG).show();
            } catch (IOException e)
            {
                LogManager.getInstance(this).logException(e);
            }
        }
        else if (itemId == R.id.menu_clear)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_clear_timestamps);
            builder.setMessage(R.string.title_clear_timestamps);

            builder.setPositiveButton(R.string.action_clear, new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int which)
                {
                    me._timestamps.clear();
                    me.saveTimestamps();

                    me.refreshList();
                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int which)
                {
                    // Do nothing...
                }
            });

            builder.create().show();
        }
        else if (itemId == R.id.menu_add_property) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_add_property);

            View view = this.getLayoutInflater().inflate(R.layout.dialog_add_property, null);
            builder.setView(view);

            builder.setPositiveButton(R.string.action_add_property, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);

                    try {
                        final JSONObject properties = new JSONObject(prefs.getString(MarkTimeActivity.SAVED_PROPERTIES, "{}"));

                        AlertDialog alertDialog = (AlertDialog) dialog;

                        final AutoCompleteTextView keyText = (AutoCompleteTextView) alertDialog.findViewById(R.id.text_dialog_property_name);
                        final AutoCompleteTextView keyValue = (AutoCompleteTextView) alertDialog.findViewById(R.id.text_dialog_property_value);

                        String key = keyText.getText().toString();
                        String value = keyValue.getText().toString();

                        if (key != null && key.length() > 0) {
                            if (value != null && value.length() > 0) {
                                properties.put(key, value);

                                me.addPropertyKey(key);
                                me.addPropertyValue(key, value);

                                SharedPreferences.Editor e = prefs.edit();
                                e.putString(MarkTimeActivity.SAVED_PROPERTIES, properties.toString());
                                e.apply();

                                me.refreshProperties();
                            } else {
                                Toast.makeText(me, R.string.toast_property_value_required, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(me, R.string.toast_property_key_required, Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Do nothing...
                }
            });

            builder.create().show();

            final AutoCompleteTextView keyText = (AutoCompleteTextView) view.findViewById(R.id.text_dialog_property_name);
            final AutoCompleteTextView keyValue = (AutoCompleteTextView) view.findViewById(R.id.text_dialog_property_value);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, this.propertyKeys());
            keyText.setAdapter(adapter);

            keyText.setThreshold(1);
            keyValue.setThreshold(1);

            keyText.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    me.updatePropertyValues(keyText.getText().toString(), keyValue);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    me.updatePropertyValues("", keyValue);
                }
            });

            keyText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    me.updatePropertyValues(keyText.getText().toString(), keyValue);
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });
        } else if (itemId == R.id.menu_change_mode) {
            if (this._mode == MarkTimeActivity.MODE_TIME_POINT) {
                this._mode = MarkTimeActivity.MODE_TIME_RANGE;
            } else if (this._mode == MarkTimeActivity.MODE_TIME_RANGE) {
                this._mode = MarkTimeActivity.MODE_TIME_POINT;
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor e = prefs.edit();
            e.putInt(MarkTimeActivity.SELECTED_MODE, this._mode);
            e.apply();

            this.updateTitle();
        }

        return true;
    }

    private void addPropertyKey(String key) {
        key = key.trim();

        if (key.length() == 0) {
            return;
        }

        JSONArray options = new JSONArray();

        options.put(key);

        String[] values = this.propertyKeys();

        for (String existingValue : values) {
            existingValue = existingValue.trim();

            if (key.equalsIgnoreCase(existingValue) == false) {
                options.put(existingValue);
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor e = prefs.edit();
        e.putString("mark_time_keys", options.toString());
        e.apply();
    }

    private void addPropertyValue(String key, String value) {
        value = value.trim();

        if (value.length() == 0) {
            return;
        }

        JSONArray options = new JSONArray();

        options.put(value);

        String[] values = this.propertyValuesForKey(key);

        for (String existingValue : values) {
            existingValue = existingValue.trim();

            if (value.equalsIgnoreCase(existingValue) == false) {
                options.put(existingValue);
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor e = prefs.edit();
        e.putString("mark_time_values_" + key.trim().toLowerCase(), options.toString());
        e.apply();
    }

    private void updatePropertyValues(String key, AutoCompleteTextView valueField) {
        String[] options = this.propertyValuesForKey(key);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options);

        valueField.setAdapter(adapter);
    }

    private String[] propertyKeys() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String[] values = new String[0];

        try {
            JSONArray keys = new JSONArray(prefs.getString("mark_time_keys", "[]"));

            values = new String[keys.length()];

            for (int i = 0; i < values.length; i++) {
                values[i] = keys.getString(i);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return values;
    }

    private String[] propertyValuesForKey(String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String[] values = new String[0];

        try {
            JSONArray keys = new JSONArray(prefs.getString("mark_time_values_" + key.trim().toLowerCase(), "[]"));

            values = new String[keys.length()];

            for (int i = 0; i < values.length; i++) {
                values[i] = keys.getString(i);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return values;
    }
}
