package com.example.localisationsmartphone;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {



    private static final String SERVER_URL = "http://192.168.1.49/localisation/createPosition.php";

    private static final int PERMISSION_REQUEST_CODE = 1;

    private double latitude  = 0;
    private double longitude = 0;
    private double altitude  = 0;
    private float  accuracy  = 0;

    private RequestQueue requestQueue;
    private TextView tvInfo;
    private TextView tvStatus;
    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvInfo   = findViewById(R.id.tvInfo);
        tvStatus = findViewById(R.id.tvStatus);
        tvLog    = findViewById(R.id.tvLog);

        requestQueue = Volley.newRequestQueue(getApplicationContext());

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            startLocationUpdates();
        }
    }

    private boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE
                },
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            tvInfo.setText("Permissions GPS refusées. Veuillez les activer dans les paramètres.");
        }
    }

    private void startLocationUpdates() {
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        tvStatus.setText("● GPS actif");
        tvStatus.setTextColor(0xFF4CAF50);

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                60000,   // intervalle minimum : 60 secondes
                150,     // distance minimum : 150 mètres
                new LocationListener() {

                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        latitude  = location.getLatitude();
                        longitude = location.getLongitude();
                        altitude  = location.getAltitude();
                        accuracy  = location.getAccuracy();

                        String msg = String.format(Locale.getDefault(),
                                "Latitude  : %.6f\nLongitude : %.6f\nAltitude  : %.1f m\nPrécision : %.1f m",
                                latitude, longitude, altitude, accuracy);

                        tvInfo.setText(msg);
                        tvStatus.setText("● Position reçue");
                        tvStatus.setTextColor(0xFF2196F3);

                        addPosition(latitude, longitude);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                        String statusStr;
                        switch (status) {
                            case LocationProvider.OUT_OF_SERVICE:
                                statusStr = "Hors service";
                                break;
                            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                                statusStr = "Temporairement indisponible";
                                break;
                            default:
                                statusStr = "Disponible";
                        }
                        log("Statut GPS : " + statusStr);
                    }

                    @Override
                    public void onProviderEnabled(@NonNull String provider) {
                        tvStatus.setText("● GPS actif");
                        tvStatus.setTextColor(0xFF4CAF50);
                        log("Fournisseur activé : " + provider);
                    }

                    @Override
                    public void onProviderDisabled(@NonNull String provider) {
                        tvStatus.setText("● GPS désactivé");
                        tvStatus.setTextColor(0xFFFF5722);
                        log("Fournisseur désactivé : " + provider);
                    }
                }
        );
    }

    private void addPosition(final double lat, final double lon) {
        StringRequest request = new StringRequest(
                Request.Method.POST,
                SERVER_URL,
                response -> {
                    log("✓ " + response);
                    Toast.makeText(getApplicationContext(), response, Toast.LENGTH_SHORT).show();
                },
                error -> {
                    log("✗ Erreur réseau : " + error.getMessage());
                    Toast.makeText(getApplicationContext(),
                            "Erreur lors de l'envoi", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                HashMap<String, String> params = new HashMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                params.put("latitude",      String.valueOf(lat));
                params.put("longitude",     String.valueOf(lon));
                params.put("date_position", sdf.format(new Date()));

                // Récupération de l'IMEI (ou identifiant alternatif)
                String imei = "unknown";
                if (ActivityCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        TelephonyManager tm =
                                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                        if (tm != null) {
                            imei = tm.getDeviceId() != null ? tm.getDeviceId() : "unknown";
                        }
                    } catch (SecurityException e) {
                        imei = "permission_denied";
                    }
                }
                params.put("imei", imei);
                return params;
            }
        };

        requestQueue.add(request);
    }

    private void log(String message) {
        runOnUiThread(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            tvLog.setText("[" + timestamp + "] " + message);
        });
    }
}
