
package com.uni.antonkluev.maps;

import android.Manifest;
import android.content.Context;
import android.graphics.Color;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DecimalFormat;
import java.util.ArrayList;
import android.content.SharedPreferences;
import com.google.maps.android.SphericalUtil;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnInfoWindowCloseListener {

    private Button clear;
    private GoogleMap map;
    private EditText pinTitle;
    private ArrayList<Marker> markers = new ArrayList<>();
    private Polygon area;
    private TextView areaDisplay;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        // Layout Components / User Input
        areaDisplay = (TextView) findViewById(R.id.areaDisplay);
        pinTitle = (EditText) findViewById(R.id.pinTitle);
        clear = (Button) findViewById(R.id.clear);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // delete markers
                for (int i = 0; i < markers.size(); i++) markers.get(i).remove();
                // clear list
                markers.clear();
                // remove area
                if (area != null) area.remove();
                // hide clear button
                clear.setVisibility(View.INVISIBLE);
                // hide area display
                if (areaDisplay.getVisibility() == View.VISIBLE)
                    areaDisplay.setVisibility(View.INVISIBLE);
            }
        });
        // Ask permission
        // https://www.youtube.com/watch?v=qS1E-Vrk60E&t=1s
        // http://www.androidhive.info/2012/07/android-gps-location-manager-tutorial/
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Check Permissions Now
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Display UI and wait for user interaction
            } else {
                ActivityCompat.requestPermissions(
                    this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
        // set current location
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                LatLng latLng = new LatLng(latitude, longitude);
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 2.0f));
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {}
        });
    }
    @Override
    protected void onStop() {
        saveSettings();
        super.onStop();
    }
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        // enable current location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return;
        map.setMyLocationEnabled(true);
        // restore settings
        restoreSettings();
        // long onclick event on map
        map.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng point) {
                // create a marker
                Marker marker = map.addMarker(
                    new MarkerOptions().position(new LatLng(point.latitude, point.longitude)));
                markers.add(marker);
                marker.setTitle("Marker "+ String.valueOf(markers.size()));
                marker.showInfoWindow();
                // show user notification
                if (markers.size() < 3) {
                    int number = 3 - markers.size();
                    String markerWord = "Marker" + (number == 1? "": "s");
                    notifyUser("Place "+ String.valueOf(number) + " more "+ markerWord);
                } else
                    // calculate area if more then 3
                    spanArea();
                // show panel for marker name edit
                showPanels(marker);
                // show clear button
                if (markers.size() > 0 && clear.getVisibility() == View.INVISIBLE)
                    clear.setVisibility(View.VISIBLE);
            }
        });
        // add events
        map.setOnMarkerClickListener(this);
        map.setOnInfoWindowCloseListener(this);
    }
    @Override
    public boolean onMarkerClick(final Marker marker) {
        showPanels(marker);
        return false;
    }
    public void showPanels (final Marker marker) {
        if (pinTitle.getVisibility() == View.INVISIBLE) {
            // pinTitle.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_down));
            pinTitle.setVisibility(View.VISIBLE);
        }
        pinTitle.setText(marker.getTitle());
        pinTitle.selectAll();
        pinTitle.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                marker.setTitle(pinTitle.getText().toString());
                marker.showInfoWindow();
                return false;
            }
        });
    }
    @Override
    public void onInfoWindowClose (Marker marker) {
        if (pinTitle.getVisibility() == View.VISIBLE) {
            // pinTitle.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up));
            pinTitle.setVisibility(View.INVISIBLE);
        }
    }
    public void spanArea() {
        // remove old area
        if (area != null) area.remove();
        // extract points from markers
        ArrayList <LatLng> points = new ArrayList <> ();
        for (int i = 0; i < markers.size(); i++)
            points.add(markers.get(i).getPosition());
        // create area
        area = map.addPolygon(new PolygonOptions()
                .strokeWidth(0)
                .fillColor(Color.argb(80, 0, 180, 255))
                .addAll(points));
        // https://developers.google.com/maps/documentation/android-api/utility/#spherical
        double areaValue = SphericalUtil.computeArea(points);
        // unit conversion
        areaValue   = areaValue > 1000000? areaValue / 1000000: areaValue;
        String unit = areaValue > 1000000? " km": " m";
        // round up
        areaValue   = Double.valueOf(new DecimalFormat("#.##").format(areaValue));
        // display area
        if (areaDisplay.getVisibility() == View.INVISIBLE)
            areaDisplay.setVisibility(View.VISIBLE);
        areaDisplay.setText(String.valueOf(areaValue) + unit +"²");
    }
    public void notifyUser(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 200);
        toast.show();
    }
    public void saveSettings() {
        Log.v("test", "invoked");
        SharedPreferences.Editor editor = this.getPreferences(Context.MODE_PRIVATE).edit();
        for (int i = 0; i < markers.size(); i ++) {
            LatLng point = markers.get(i).getPosition();
            editor.putString("marker_" + String.valueOf(i),
                    markers.get(i).getTitle() + "," +
                            String.valueOf(point.latitude) + "," +
                            String.valueOf(point.longitude));
        }
        editor.putInt("size", markers.size());
        editor.commit();
    }
    public void restoreSettings() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        for (int i = 0; i < sharedPref.getInt("size", 0); i ++) {
            String[] rawMarker = sharedPref.getString("marker_" + String.valueOf(i), "").split(",");
            markers.add(map.addMarker(new MarkerOptions()
                .position(new LatLng(
                    Double.parseDouble(rawMarker[1]),
                    Double.parseDouble(rawMarker[2])))
                .title(rawMarker[0])));
        }
        // show clear button
        if (markers.size() > 0) {
            if (clear.getVisibility() == View.INVISIBLE) clear.setVisibility(View.VISIBLE);
            spanArea();
        }
    }
}
