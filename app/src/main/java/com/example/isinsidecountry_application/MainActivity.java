package com.example.isinsidecountry_application;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.cardview.widget.CardView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.isincountry.sdk.IsInCountrySDK;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final String SERVER_URL = "https://poetic-elegance-production-2172.up.railway.app";

    // UI Components
    private AutoCompleteTextView autoCompleteCountry;
    private EditText editTextLatitude;
    private EditText editTextLongitude;
    private Button buttonSubmit;
    private CardView cardViewStatus;
    private ImageView imageViewStatusIcon;
    private TextView textViewStatusTitle;
    private TextView textViewStatusMessage;

    // Data
    private IsInCountrySDK sdk;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private String selectedCountryCode = "";

    // Country data
    private final Map<String, String> countryMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        setupClickListeners();
        checkLocationPermission();
        setupDropdownListener();

        // Initialize SDK first
        initializeSDK();

        // Auto-fetch GPS coordinates on startup
        fetchCurrentLocation();

        // Fetch countries from server
        fetchCountriesFromServer();
    }

    private void initializeSDK() {
        try {
            sdk = IsInCountrySDK.initialize(this, SERVER_URL);
        } catch (Exception e) {
            showError("Failed to initialize SDK: " + e.getMessage());
        }
    }

    private void initializeViews() {
        autoCompleteCountry = findViewById(R.id.autoCompleteCountry);
        editTextLatitude = findViewById(R.id.editTextLatitude);
        editTextLongitude = findViewById(R.id.editTextLongitude);
        buttonSubmit = findViewById(R.id.buttonSubmit);
        cardViewStatus = findViewById(R.id.cardViewStatus);
        imageViewStatusIcon = findViewById(R.id.imageViewStatusIcon);
        textViewStatusTitle = findViewById(R.id.textViewStatusTitle);
        textViewStatusMessage = findViewById(R.id.textViewStatusMessage);
    }

    private void setupClickListeners() {
        buttonSubmit.setOnClickListener(v -> submitLocationCheck());
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void fetchCurrentLocation() {
        if (sdk == null) {
            initializeSDK();
            if (sdk == null) return;
        }

        if (!sdk.hasLocationPermission()) {
            return;
        }

        try {
            sdk.getCurrentLocation(new IsInCountrySDK.LocationCallback() {
                @Override
                public void onLocationReceived(double latitude, double longitude) {
                    runOnUiThread(() -> {
                        currentLatitude = latitude;
                        currentLongitude = longitude;

                        // Format coordinates with proper precision
                        DecimalFormat coordFormat = new DecimalFormat("0.########");
                        String formattedLat = coordFormat.format(latitude);
                        String formattedLon = coordFormat.format(longitude);

                        // Update the text fields
                        editTextLatitude.setText(formattedLat);
                        editTextLongitude.setText(formattedLon);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> showError("Failed to get GPS location: " + error));
                }
            });
        } catch (SecurityException e) {
            showError("Location permission denied: " + e.getMessage());
        }
    }

    private void submitLocationCheck() {
        // Get the selected country name from dropdown
        String selectedCountryName = autoCompleteCountry.getText().toString().trim();

        if (TextUtils.isEmpty(selectedCountryName)) {
            showError("Please select a country from the dropdown");
            return;
        }

        // Get the country code from the selected country name
        selectedCountryCode = getKeyByValue(countryMap, selectedCountryName);

        if (selectedCountryCode == null) {
            showError("Invalid country selection");
            return;
        }

        if (currentLatitude == 0.0 && currentLongitude == 0.0) {
            showError("No GPS coordinates available. Please wait for location fetch or check permissions.");
            return;
        }

        if (sdk == null) {
            initializeSDK();
            if (sdk == null) return;
        }

        // Show loading status
        showFloatingResult("CHECKING LOCATION...",
            "Checking if you are in " + selectedCountryName + " (" + selectedCountryCode + ")",
            R.drawable.ic_gps, false);

        sdk.checkCoordinateInCountry(currentLatitude, currentLongitude, selectedCountryCode, new IsInCountrySDK.LocationCheckCallback() {
            @Override
            public void onResult(boolean isInCountry, String message) {
                runOnUiThread(() -> {
                    String resultTitle = isInCountry ? "✓ INSIDE COUNTRY" : "✗ OUTSIDE COUNTRY";
                    String resultMessage = "Location: " + currentLatitude + ", " + currentLongitude +
                            "\nCountry: " + selectedCountryName + " (" + selectedCountryCode + ")" +
                            "\nResult: " + (isInCountry ? "You are currently inside this country!" : "You are currently outside this country.");

                    showFloatingResult(resultTitle, resultMessage,
                        isInCountry ? R.drawable.ic_inside_country : R.drawable.ic_outside_country,
                        isInCountry);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showFloatingResult("✗ ERROR", "Failed to check location: " + error,
                        R.drawable.ic_outside_country, false);
                });
            }
        });
    }

    private void showFloatingResult(String title, String message, int iconRes, boolean isSuccess) {
        // Update content
        imageViewStatusIcon.setImageResource(iconRes);
        textViewStatusTitle.setText(title);
        textViewStatusMessage.setText(message);

        // Set background color based on result
        cardViewStatus.setCardBackgroundColor(
            ContextCompat.getColor(this, isSuccess ? R.color.success_light : R.color.error_light)
        );

        // Show with animation
        cardViewStatus.setVisibility(View.VISIBLE);
        startFloatingAnimation(cardViewStatus);

        // Auto-dismiss after 5 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (cardViewStatus.getVisibility() == View.VISIBLE) {
                cardViewStatus.setVisibility(View.GONE);
            }
        }, 5000);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setupDropdownListener() {
        autoCompleteCountry.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = (String) parent.getItemAtPosition(position);
            selectedCountryCode = getKeyByValue(countryMap, selectedItem);
        });
    }

    private String getKeyByValue(Map<String, String> map, String value) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void startFloatingAnimation(View view) {
        // Create elegant floating animation
        view.setAlpha(0f);
        view.setTranslationY(-50f);
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);

        // Animate entrance
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void fetchCountriesFromServer() {
        if (sdk == null) {
            initializeSDK();
            if (sdk == null) return;
        }

        sdk.getCountries(new IsInCountrySDK.CountriesCallback() {
            @Override
            public void onResult(com.example.isincountry.sdk.ApiClient.CountriesResponse response) {
                runOnUiThread(() -> {
                    try {
                        // Log the full response for debugging
                        android.util.Log.d("CountriesDebug", "Response received: " + (response != null ? "Not null" : "NULL"));

                        if (response != null) {
                            android.util.Log.d("CountriesDebug", "Response success: " + response.success);
                            android.util.Log.d("CountriesDebug", "Response count: " + response.count);
                            android.util.Log.d("CountriesDebug", "Response data: " + (response.data != null ? "Not null, length: " + response.data.length : "NULL"));
                            android.util.Log.d("CountriesDebug", "Response error: " + response.error);
                        }

                        // Clear existing data
                        countryMap.clear();

                        if (response != null && response.success && response.data != null) {
                            List<String> countryNames = new ArrayList<>();

                            android.util.Log.d("CountriesDebug", "Processing " + response.data.length + " countries");

                            // Populate country map and names list
                            for (com.example.isincountry.sdk.ApiClient.Country country : response.data) {
                                android.util.Log.d("CountriesDebug", "Processing country: " +
                                    (country != null ? ("code=" + country.country_code + ", name=" + country.country_name) : "NULL"));

                                if (country != null && country.country_code != null && country.country_name != null) {
                                    countryMap.put(country.country_code, country.country_name);
                                    countryNames.add(country.country_name);
                                    android.util.Log.d("CountriesDebug", "Added country: " + country.country_code + " - " + country.country_name);
                                } else if (country != null) {
                                    // Log the actual object to see what fields are available
                                    android.util.Log.d("CountriesDebug", "Country object: " + country.toString());
                                }
                            }

                            // Set up the autocomplete adapter
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                MainActivity.this,
                                android.R.layout.simple_dropdown_item_1line,
                                countryNames
                            );
                            autoCompleteCountry.setAdapter(adapter);
                            autoCompleteCountry.setThreshold(1);

                            showError("Loaded " + countryNames.size() + " countries successfully");
                        } else {
                            if (response == null) {
                                showError("No response received from server");
                                android.util.Log.e("CountriesDebug", "Response is null");
                            } else if (!response.success) {
                                showError("Server returned error: " + response.error);
                                android.util.Log.e("CountriesDebug", "Server error: " + response.error);
                            } else {
                                showError("No countries data received from server");
                                android.util.Log.e("CountriesDebug", "Response data is null");
                            }
                            setupFallbackCountries();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("CountriesDebug", "Exception processing countries: " + e.getMessage(), e);
                        showError("Error processing countries data: " + e.getMessage());
                        setupFallbackCountries();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    android.util.Log.e("CountriesDebug", "Network error: " + error);
                    showError("Failed to fetch countries: " + error);
                    // Set up with default fallback countries if needed
                    setupFallbackCountries();
                });
            }
        });
    }

    private void setupFallbackCountries() {
        // Fallback countries in case server fetch fails
        countryMap.clear();
        countryMap.put("US", "United States");
        countryMap.put("CA", "Canada");
        countryMap.put("GB", "United Kingdom");
        countryMap.put("FR", "France");
        countryMap.put("DE", "Germany");
        countryMap.put("IT", "Italy");
        countryMap.put("ES", "Spain");
        countryMap.put("AU", "Australia");
        countryMap.put("JP", "Japan");
        countryMap.put("IL", "Israel");

        List<String> countryNames = new ArrayList<>(countryMap.values());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            countryNames
        );
        autoCompleteCountry.setAdapter(adapter);
        autoCompleteCountry.setThreshold(1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                fetchCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission denied. GPS coordinates cannot be fetched.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
