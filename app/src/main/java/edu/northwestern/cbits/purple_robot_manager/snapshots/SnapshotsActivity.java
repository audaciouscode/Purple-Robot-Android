package edu.northwestern.cbits.purple_robot_manager.snapshots;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.RobotContentProvider;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;

public class SnapshotsActivity extends AppCompatActivity
{
    @SuppressLint("SimpleDateFormat")
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.layout_snapshots_activity);

        this.getSupportActionBar().setTitle(R.string.title_snapshots);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    protected void onResume()
    {
        super.onResume();

        this.refresh();
    }

    public void refresh()
    {
        final SnapshotsActivity me = this;

        ListView listView = (ListView) this.findViewById(R.id.list_snapshots);

        final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, H:mm:ss");

        CursorLoader c = new CursorLoader(this, RobotContentProvider.SNAPSHOTS, null, null, null, "recorded DESC");

        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.layout_snapshot_row, c.loadInBackground(), new String[0], new int[0], 0)
        {
            public void bindView(View view, Context context, Cursor cursor)
            {
                final String source = cursor.getString(cursor.getColumnIndex("source"));
                final String audioFile = cursor.getString(cursor.getColumnIndex("audio_file"));

                ImageView micIcon = (ImageView) view.findViewById(R.id.mic_icon);

                if (audioFile != null)
                    micIcon.setVisibility(View.VISIBLE);
                else
                    micIcon.setVisibility(View.GONE);

                Date date = new Date(cursor.getLong(cursor.getColumnIndex("recorded")));

                TextView dateLabel = (TextView) view.findViewById(R.id.date_label);
                dateLabel.setText(sdf.format(date));

                TextView sourceLabel = (TextView) view.findViewById(R.id.source_label);

                try
                {
                    JSONArray array = new JSONArray(cursor.getString(cursor.getColumnIndex("value")));

                    if (array.length() == 1)
                        sourceLabel.setText(me.getString(R.string.snapshot_single_desc, source));
                    else
                        sourceLabel.setText(me.getString(R.string.snapshot_desc, source, array.length()));
                }
                catch (JSONException e)
                {
                    LogManager.getInstance(me).logException(e);

                    sourceLabel.setText(source);
                }
            }
        };

        listView.setOnItemClickListener(new OnItemClickListener()
        {
            public void onItemClick(AdapterView<?> listView, View view1, int which, long id)
            {
                Intent intent = new Intent(me, SnapshotActivity.class);
                intent.putExtra("id", id);

                me.startActivity(intent);
            }
        });

        listView.setAdapter(adapter);
    }

    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.menu_snapshot_activity, menu);

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        final SnapshotsActivity me = this;

        int itemId = item.getItemId();

        if (itemId == android.R.id.home)
            this.finish();
        else if (itemId == R.id.menu_snapshot)
        {
            String label = this.getString(R.string.snapshot_user_initiated);

            try
            {
                SnapshotManager.getInstance(this).takeSnapshot(this, label, new Runnable()
                {
                    public void run()
                    {
                        me.runOnUiThread(new Runnable()
                        {
                            public void run()
                            {
                                me.refresh();
                            }
                        });
                    }
                });
            }
            catch (EmptySnapshotException e)
            {
                Toast.makeText(this, R.string.empty_snapshot_error, Toast.LENGTH_SHORT).show();
            }
        }

        return true;
    }
}
