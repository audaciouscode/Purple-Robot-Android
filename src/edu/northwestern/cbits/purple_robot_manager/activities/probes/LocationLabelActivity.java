package edu.northwestern.cbits.purple_robot_manager.activities.probes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.calibration.LocationCalibrationHelper;
import edu.northwestern.cbits.purple_robot_manager.db.ProbeValuesProvider;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityManager;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.FusedLocationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.LocationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.RawLocationProbe;
import edu.northwestern.cbits.purple_robot_manager.util.DBSCAN;
import edu.northwestern.cbits.purple_robot_manager.util.DBSCAN.Cluster;
import edu.northwestern.cbits.purple_robot_manager.util.DBSCAN.Point;

public class LocationLabelActivity extends AppCompatActivity
{
    private ArrayList<Cluster> _clusters = new ArrayList<>();
    private int _selectedCluster = -1;

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.layout_location_label_activity);

        DBSCAN dbscan = new DBSCAN(this, DBSCAN.DISTANCE, DBSCAN.POPULATION);

        Cursor cursor = ProbeValuesProvider.getProvider(this).retrieveValues(this, LocationProbe.DB_TABLE,
                LocationProbe.databaseSchema());

        while (cursor.moveToNext())
        {
            dbscan.addPoint(new Point(cursor.getDouble(cursor.getColumnIndex(LocationProbe.LATITUDE_KEY)), cursor
                    .getDouble(cursor.getColumnIndex(LocationProbe.LONGITUDE_KEY))));
        }

        cursor.close();

        cursor = ProbeValuesProvider.getProvider(this).retrieveValues(this, RawLocationProbe.DB_TABLE,
                RawLocationProbe.databaseSchema());

        while (cursor.moveToNext())
        {
            dbscan.addPoint(new Point(cursor.getDouble(cursor.getColumnIndex(RawLocationProbe.LATITUDE_KEY)), cursor
                    .getDouble(cursor.getColumnIndex(RawLocationProbe.LONGITUDE_KEY))));
        }

        cursor.close();

        cursor = ProbeValuesProvider.getProvider(this).retrieveValues(this, FusedLocationProbe.DB_TABLE,
                FusedLocationProbe.databaseSchema());

        while (cursor.moveToNext())
        {
            dbscan.addPoint(new Point(cursor.getDouble(cursor.getColumnIndex(FusedLocationProbe.LATITUDE_KEY)), cursor
                    .getDouble(cursor.getColumnIndex(FusedLocationProbe.LONGITUDE_KEY))));
        }

        cursor.close();

        this._clusters.addAll(dbscan.calculate(this));

        Collections.sort(this._clusters, new Comparator<Cluster>()
        {
            public int compare(Cluster one, Cluster two)
            {
                if (one.population() > two.population())
                    return -1;
                else if (one.population() < two.population())
                    return 1;

                return 0;
            }
        });

        MapsInitializer.initialize(this);

        this.getSupportActionBar().setTitle(R.string.title_location_label);
        this.getSupportActionBar().setSubtitle(R.string.title_location_desc);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Editor e = PreferenceManager.getDefaultSharedPreferences(this).edit();
        e.putLong("last_location_calibration", System.currentTimeMillis());
        e.apply();
    }

    protected void onResume()
    {
        super.onResume();

        SupportMapFragment fragment = (SupportMapFragment) this.getSupportFragmentManager().findFragmentById(
                R.id.map_fragment);

        final LocationLabelActivity me = this;

        fragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap map) {

                for (int i = 0; i < me._clusters.size(); i++)
                {
                    Cluster cluster = me._clusters.get(i);

                    int color = Color.BLACK;

                    switch (i % 5)
                    {
                        case 0:
                            color = Color.parseColor("#33B5E5");
                            break;
                        case 1:
                            color = Color.parseColor("#AA66CC");
                            break;
                        case 2:
                            color = Color.parseColor("#99CC00");
                            break;
                        case 3:
                            color = Color.parseColor("#FFBB33");
                            break;
                        case 4:
                            color = Color.parseColor("#FF4444");
                            break;
                    }

                    for (Point p : cluster.getPoints(20))
                    {
                        CircleOptions options = new CircleOptions();
                        options.center(new LatLng(p.x(), p.y()));
                        options.fillColor(color);
                        options.strokeColor(color);
                        options.strokeWidth(20.0f);
                        options.radius(10);

                        map.addCircle(options);
                    }
                }

                ListView list = (ListView) me.findViewById(R.id.list_view);
                me.registerForContextMenu(list);

                ArrayAdapter<Cluster> adapter = new ArrayAdapter<Cluster>(me, R.layout.layout_cluster_row, me._clusters)
                {
                    public View getView(final int position, View convertView, final ViewGroup parent)
                    {
                        if (convertView == null)
                        {
                            LayoutInflater inflater = (LayoutInflater) me.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                            convertView = inflater.inflate(R.layout.layout_cluster_row, null);
                        }

                        int color = Color.BLACK;

                        switch (position % 5)
                        {
                            case 0:
                                color = Color.parseColor("#33B5E5");
                                break;
                            case 1:
                                color = Color.parseColor("#AA66CC");
                                break;
                            case 2:
                                color = Color.parseColor("#99CC00");
                                break;
                            case 3:
                                color = Color.parseColor("#FFBB33");
                                break;
                            case 4:
                                color = Color.parseColor("#FF4444");
                                break;
                        }

                        Cluster c = me._clusters.get(position);

                        View v = convertView.findViewById(R.id.color_indicator);
                        v.setBackgroundColor(color);

                        TextView clusterName = (TextView) convertView.findViewById(R.id.text_cluster_name);
                        TextView clusterDetails = (TextView) convertView.findViewById(R.id.text_cluster_details);

                        String name = c.getName();

                        if (name == null)
                            name = me.getString(R.string.title_place_unknown);

                        clusterName.setText(name);
                        clusterDetails.setText(me.getString(R.string.title_place_count, c.population()));

                        v.setOnClickListener(new OnClickListener()
                        {
                            public void onClick(View arg0)
                            {
                                Cluster cluster = me._clusters.get(position);

                                double minX = Double.MAX_VALUE;
                                double minY = Double.MAX_VALUE;
                                double maxX = 0 - Double.MAX_VALUE;
                                double maxY = 0 - Double.MAX_VALUE;

                                for (Point p : cluster.getPoints())
                                {
                                    double x = p.x();
                                    double y = p.y();

                                    if (minX > x)
                                        minX = x;

                                    if (minY > y)
                                        minY = y;

                                    if (maxX < x)
                                        maxX = x;

                                    if (maxY < y)
                                        maxY = y;
                                }

                                LatLngBounds bounds = new LatLngBounds(new LatLng(minX, minY), new LatLng(maxX, maxY));

                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, parent.getWidth() / 2,
                                        parent.getHeight() / 2, 20));
                            }
                        });

                        return convertView;
                    }
                };

                if (me._clusters.size() > 0)
                {
                    Cluster cluster = me._clusters.get(0);

                    double minX = Double.MAX_VALUE;
                    double minY = Double.MAX_VALUE;
                    double maxX = 0 - Double.MAX_VALUE;
                    double maxY = 0 - Double.MAX_VALUE;

                    for (Point p : cluster.getPoints())
                    {
                        double x = p.x();
                        double y = p.y();

                        if (minX > x)
                            minX = x;

                        if (minY > y)
                            minY = y;

                        if (maxX < x)
                            maxX = x;

                        if (maxY < y)
                            maxY = y;
                    }

                    LatLngBounds bounds = new LatLngBounds(new LatLng(minX, minY), new LatLng(maxX, maxY));
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200, 200, 20));
                }

                list.setAdapter(adapter);

                list.setOnItemClickListener(new OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        me._selectedCluster = position;

                        parent.showContextMenu();
                    }
                });

            }
        });
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.setHeaderTitle(R.string.menu_label_cluster);

        String[] groups = this.getResources().getStringArray(R.array.place_groups);

        for (int i = 0; i < groups.length; i++)
        {
            String group = groups[i];

            menu.add(Menu.NONE, i, i, group);
        }
    }

    public boolean onContextItemSelected(MenuItem item)
    {
        Cluster selected = this._clusters.get(this._selectedCluster);

        selected.setName(item.getTitle().toString());

        ListView list = (ListView) this.findViewById(R.id.list_view);
        list.setAdapter(list.getAdapter());

        DBSCAN.persistClusters(this, this._clusters, DBSCAN.DISTANCE, DBSCAN.POPULATION);

        return true;
    }

    protected void onPause()
    {
        super.onPause();

        LocationCalibrationHelper.check(this);
        SanityManager.getInstance(this).refreshState(this);
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
            this.finish();

        return true;
    }
}
