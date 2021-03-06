package net.rofael.uabparkingfinder;

import java.util.ArrayList;
import java.util.Collections;


import android.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.*;
import android.widget.AdapterView;
import android.view.View;

import android.widget.Button;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.database.*;
import com.squareup.picasso.Picasso;

import java.util.Locale;

public class ParkingActivity extends AppCompatActivity implements OnItemSelectedListener, LocationListener {

    private Parking lot;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parking);

        // Receives name of parking lot from selection in main menu
        lot = (Parking) getIntent().getParcelableExtra("Parking");
        TextView setParkingName = (TextView) findViewById(R.id.parking_name);
        final TextView setParkingStatus = (TextView) findViewById(R.id.parking_status);

        // Initializes connection to Google Firebase and checks for mReports from server
        mDatabase = FirebaseDatabase.getInstance().getReference();
        checkFirebase();

        // Sets text based on the selection
        setParkingName.setText(lot.toString());
        mDatabase.child("overall").child(lot.toString()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int overallStatus = -1;
                if (dataSnapshot.getValue() != null) {
                    long getRaw = (long) dataSnapshot.getValue();
                    overallStatus = Integer.parseInt(Long.toString(getRaw));
                }
                switch (overallStatus) {
                    case -1:
                        setParkingStatus.setText(R.string.no_reports);
                        break;
                    case 0:
                        setParkingStatus.setText(R.string.light_parking);
                        break;
                    case 1:
                        setParkingStatus.setText(R.string.medium_parking);
                        break;
                    case 2:
                        setParkingStatus.setText(R.string.heavy_parking);
                        break;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        // Sets up the mDrop down box for report selection
        final Spinner dropDownBox = (Spinner) findViewById(R.id.status_selection);
        ArrayAdapter<CharSequence> dropDownBoxAdapter = ArrayAdapter.createFromResource(this, R.array.reports_array, android.R.layout.simple_spinner_item);
        dropDownBoxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dropDownBox.setAdapter(dropDownBoxAdapter);
        dropDownBox.setOnItemSelectedListener(this);
        mDrop = dropDownBox;

        // Initializes the Send button
        Button confirmClick = (Button) findViewById(R.id.send_staus);
        confirmClick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addToList();
            }
        });

        mReportListAdapter = new ReportListAdapter(this, mReports);
        final ListView reportList = (ListView) findViewById(R.id.recent_reports_table);
        reportList.setAdapter(mReportListAdapter);

        // Code for the swipe refresh
        final SwipeRefreshLayout listSwipe = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_report_list);
        listSwipe.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        mReportListAdapter.notifyDataSetChanged();
                        listSwipe.setRefreshing(false);
                    }
                }
        );

        // Populates map image
        ImageView map = (ImageView) findViewById(R.id.directions);
        map.setImageResource(R.drawable.unk);

        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PackageManager.PERMISSION_GRANTED);
            String build = String.format(Locale.ENGLISH,"https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=13&size=300x300&markers=color:red|%f,%f",lot.getLat(),lot.getLon(),lot.getLat(),lot.getLon());
            Picasso.with(this).load(build).into(map);
        }
        else
        {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            mCurrentLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            double currlat = mCurrentLocation.getLatitude();
            double currlon = mCurrentLocation.getLongitude();
            String build = String.format(Locale.ENGLISH, "https://maps.googleapis.com/maps/api/staticmap?markers=color:blue||%f,%f&markers=color:red|%f,%f&size=300x300", currlat, currlon, lot.getLat(), lot.getLon());
            Picasso.with(this).load(build).into(map);
        }


        map.bringToFront();

        map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String gmmIntentUri = String.format(Locale.ENGLISH,"https://www.google.com/maps/search/?api=1&query=%f,%f",lot.getLat(),lot.getLon());
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(gmmIntentUri));
                startActivity(mapIntent);
            }
        });

    }

    public void onLocationChanged(Location location)
    {
        if (location != null)
        {
            mLocationManager.removeUpdates(this);
        }
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Parking Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {

    }

    public void onNothingSelected(AdapterView<?> parent) {

    }

    // Creates a new report and sends it to Firebase
    public void addToList() {
        mReportType = mDrop.getSelectedItemPosition() - 1;
        Report rep = new Report(lot,mReportType);
        if (mReportType > -1 && mReportType < 3) {
            mDatabase.child("reports").child(lot.toString()).push().setValue(rep);
            checkFirebase();
        }



    }

    // Checks mReports from Google Firebase. Downloads them if we don't have them.
    private void checkFirebase()
    {

        mDatabase.child("reports").child(lot.toString()).limitToLast(10).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot reportSnapshot: dataSnapshot.getChildren())
                {
                    // Adds a report from Firebase to the list if it hasn't been downloaded
                    long reportTime = (long) reportSnapshot.child("reportTime").getValue();
                    long reportStatus = (long) reportSnapshot.child("status").getValue();
                    int reportStat = Integer.parseInt(Long.toString(reportStatus));
                    Report rep = new Report(lot,reportStat,reportTime);
                    if (!mReports.contains(rep))
                    {
                        mReports.add(rep);
                    }
                }

                // Populates the text list of mReports based on the report list
                Collections.sort(mReports, new ReportComparator());
                mReportListAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });


    }

    public void onProviderDisabled(String arg0) {}
    public void onProviderEnabled(String arg0) {}
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}


    private int mReportType;
    private ArrayList<Report> mReports = new ArrayList<Report>();
    private ReportListAdapter mReportListAdapter;
    private Spinner mDrop;
    private DatabaseReference mDatabase;
    private Location mCurrentLocation;
    LocationManager mLocationManager;
}
