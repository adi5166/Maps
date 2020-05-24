package com.example.lab7;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleMap.OnMapLoadedCallback,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnMapLongClickListener,
        SensorEventListener {

    private static final int MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 101;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback locationCallback;
    Marker gpsMarker = null;
    List<Marker> markerList;

    static public SensorManager sensorManager;
    private Sensor sensor;
    private boolean acceltype;
    private final String MARKER_JSON = "markers.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        markerList = new ArrayList<>();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            acceltype = true;
        } else {
            acceltype = false;
        }

        //TODO wczytywanie z JSON
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLoadedCallback(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapLongClickListener(this);

        restoreFromJson();
    }


    @Override
    public void onMapLoaded() {
        Log.i(MapsActivity.class.getSimpleName(), "MapLoaded");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }
        Task<Location> lastLocation = fusedLocationClient.getLastLocation();

        lastLocation.addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null && mMap != null) {
                    mMap.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                            .title(getString(R.string.last_know_loc_msg)));
                }
            }
        });
        createLoactionRequest();
        createLocationCallback();
        startLocationUpdates();
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(latLng.latitude, latLng.longitude))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                .alpha(0.8f)
                .title(String.format("Position:(%.3f, %.3f)", latLng.latitude, latLng.longitude)));
        markerList.add(marker);

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        CameraPosition cameraPosition = mMap.getCameraPosition();
        if (cameraPosition.zoom < 14f)
            mMap.moveCamera(CameraUpdateFactory.zoomTo(14f));
        showButtons();
        return false;
    }

    private void createLoactionRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        if (locationCallback != null)
            fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                //super.onLocationResult(locationResult);
                if (locationResult != null) {
                    if (gpsMarker != null)
                        gpsMarker.remove();
                    Location location = locationResult.getLastLocation();
                    //Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marker);
                    gpsMarker = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(location.getLatitude(), location.getLongitude()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            .alpha(0.8f)
                            .title("Current Location"));
                }
            }
        };
    }

    public void zoomInClick(View view) {
        mMap.moveCamera(CameraUpdateFactory.zoomIn());
    }

    public void zoomOutClick(View view) {
        mMap.moveCamera((CameraUpdateFactory.zoomOut()));
    }


    public void hideButtons(View view) {
        FloatingActionButton button = findViewById(R.id.hide_button);
        button.setVisibility(View.INVISIBLE);
        button = findViewById(R.id.accel_button);
        button.setVisibility(View.INVISIBLE);
        /*
        TextView textView = findViewById(R.id.accel_text);
        textView.setVisibility(View.INVISIBLE);
         */
        //TODO animacja chowania przycisków
    }

    public void showButtons() {
        FloatingActionButton button = findViewById(R.id.hide_button);
        button.setVisibility(View.VISIBLE);
        button = findViewById(R.id.accel_button);
        button.setVisibility(View.VISIBLE);
        //TODO animacja pojawiania się przycisków
    }

    public void clearMemory(View view) {
        markerList.clear();
        mMap.clear();
        hideButtons(view);
        TextView textView = findViewById(R.id.accel_text);
        textView.setVisibility(View.INVISIBLE);
    }

    public void startAccel(View view) {
        TextView textView = findViewById(R.id.accel_text);
        if (textView.getVisibility() == View.INVISIBLE)
            textView.setVisibility(View.VISIBLE);
        else
            textView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        StringBuilder stringBuilder = new StringBuilder();
        if (acceltype)
            stringBuilder.append(String.format("Acceleration:\nx = %.4f\ny = %.4f\nz = %.4f", event.values[0], event.values[1], event.values[2]));
        else
            stringBuilder.append("Acceleration\nsensor wasn\'t found");
        TextView textView = findViewById(R.id.accel_text);
        textView.setText(stringBuilder.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensor != null)
            MapsActivity.sensorManager.registerListener(this, sensor, 100000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveToJson();
    }
/*
    private void saveToJson() {
        Gson gson = new Gson();
        String listMarker = gson.toJson(markerList);
        FileOutputStream outputStream;
        try {
            outputStream = openFileOutput(MARKER_JSON, MODE_PRIVATE);
            FileWriter writer = new FileWriter(outputStream.getFD());
            writer.write(listMarker);
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
*/

    private void saveToJson() {
        FileOutputStream outputStream;

        JSONArray jsonArray = new JSONArray();
        JSONObject finalObject = new JSONObject();


        for (int i = 0; i < markerList.size(); i++) {
            try {
                JSONObject object = new JSONObject();
                object.put("latitude", markerList.get(i).getPosition().latitude);
                object.put("longitude", markerList.get(i).getPosition().longitude);
                object.put("title", markerList.get(i).getTitle());
                //object.put("alpha", markerList.get(i).getAlpha());
                jsonArray.put(object);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        try {
            finalObject.put("marker", jsonArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            outputStream = openFileOutput(MARKER_JSON, MODE_PRIVATE);
            FileWriter writer = new FileWriter(outputStream.getFD());
            writer.write(finalObject.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void restoreFromJson() {
        FileInputStream inputStream;
        int DEFAULT_BUFFER_SIZE = 100000;
        Gson gson = new Gson();
        String readJson;
        try {
            inputStream = openFileInput(MARKER_JSON);
            FileReader reader = new FileReader(inputStream.getFD());
            char[] buf = new char[DEFAULT_BUFFER_SIZE];
            int n;
            StringBuilder builder = new StringBuilder();
            while ((n = reader.read(buf)) >= 0) {
                String tmp = String.valueOf(buf);
                String substring = (n < DEFAULT_BUFFER_SIZE) ? tmp.substring(0, n) : tmp;
                builder.append(substring);
            }
            reader.close();

            readJson = builder.toString();
            JSONObject jsonObject = new JSONObject(readJson);
            JSONArray markerJson = jsonObject.getJSONArray("marker");
            JSONObject object1;

            for (int i = 0; i < markerJson.length(); i++) {
                object1 = markerJson.getJSONObject(i);
                LatLng tee = new LatLng(object1.getDouble("latitude"), object1.getDouble("longitude"));
                String tt = object1.getString("title");
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(object1.getDouble("latitude"), object1.getDouble("longitude")))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                        .alpha(0.8f)
                        .title(object1.getString("title")));
                markerList.add(marker);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /*
        public void restoreFromJson() {
            FileInputStream inputStream;
            int DEFAULT_BUFFER_SIZE = 10000;
            Gson gson = new Gson();
            String readJson;
            try {
                inputStream = openFileInput(MARKER_JSON);
                FileReader reader = new FileReader(inputStream.getFD());
                char[] buf = new char[DEFAULT_BUFFER_SIZE];
                int n;
                StringBuilder builder = new StringBuilder();
                while ((n = reader.read(buf)) >= 0) {
                    String tmp = String.valueOf(buf);
                    String substring = (n < DEFAULT_BUFFER_SIZE) ? tmp.substring(0, n) : tmp;
                    builder.append(substring);
                }
                reader.close();
                readJson = builder.toString();
                Type collectionType = new TypeToken<List<Marker>>() {
                }.getType();
                List<Marker> o = gson.fromJson(readJson, collectionType);
                if (o != null) {
                    markerList.clear();
                    mMap.clear();
                    for (int i = 0; i < o.size(); i++) {
                        Marker marker = mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(o.get(i).getPosition().latitude, o.get(i).getPosition().longitude))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                                .alpha(0.8f)
                                .title(String.format("Position:(%.3f, %.3f)", o.get(i).getPosition().latitude, o.get(i).getPosition().longitude)));
                        markerList.add(marker);
                    }
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
