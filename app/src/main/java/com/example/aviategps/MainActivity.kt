package com.example.aviategps

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import kotlin.math.roundToInt


class AviateGPSActivity : AppCompatActivity(), LocationListener, OnMapReadyCallback {


    private lateinit var locationManager: LocationManager
    private lateinit var targetLocation: Location
    private lateinit var mMap: GoogleMap
    private var currentLocationMarker: Marker? = null
    private var targetLocationMarker: Marker? = null

    private lateinit var locationTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var headingTextView: TextView
    private lateinit var distanceTextView: TextView
    private var greatCircleLine: Polyline? = null

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var mapToggle: Switch
    private var distance = 0f // Initialize distance here
    private lateinit var placesClient: PlacesClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Initialize views
        locationTextView = findViewById(R.id.locationTextView)
        speedTextView = findViewById(R.id.speedTextView)
        headingTextView = findViewById(R.id.headingTextView)
        distanceTextView = findViewById(R.id.distanceTextView)

        // Initialize location manager and target location
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        targetLocation = Location("Target").apply {
            latitude = 41.41439737031373 // Example: CLE's latitude
            longitude = -81.84595833196927 // Example: CLE's longitude
        }

        // Check and request permissions if necessary
        checkAndRequestPermissions()

        // Initialize map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize map toggle switch
        mapToggle = findViewById(R.id.mapToggle)
        mapToggle.setOnCheckedChangeListener { _, isChecked ->
            toggleMapView(isChecked)
        }

        val satelliteToggle = findViewById<Switch>(R.id.satelliteToggle)
        satelliteToggle.setOnCheckedChangeListener { _, isChecked ->
            mMap.mapType =
                if (isChecked) GoogleMap.MAP_TYPE_SATELLITE else GoogleMap.MAP_TYPE_NORMAL
        }

        // Initialize PlacesClient
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyBYKKWExJ-kv3LGUJjiS_ZG-X6qrW5lyHI")
        }
        placesClient = Places.createClient(this)

        // Set up AutocompleteSupportFragment
        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG
            )
        )
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                targetLocation = Location("Target").apply {
                    latitude = place.latLng!!.latitude
                    longitude = place.latLng!!.longitude
                }
                updateTargetMarker()
                distance = 0f // reset distance
            }

            val TAG = "UpdatingTargetMarker"

            override fun onError(status: Status) {
                Log.d(TAG, "An error occurred: $status")
            }
        })

    }

    private fun updateTargetMarker() {
        targetLocationMarker?.remove()
        val targetLatLng = LatLng(targetLocation.latitude, targetLocation.longitude)
        targetLocationMarker =
            mMap.addMarker(MarkerOptions().position(targetLatLng).title("Target Location"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, 15f))
        updateGreatCircleLine(currentLocationMarker!!.position, targetLatLng)
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions if necessary
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        } else {
            // Permissions already granted, proceed with location updates and map initialization
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
            if (::mMap.isInitialized && mMap != null) {
                mMap.isMyLocationEnabled = true
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, proceed with location updates and map initialization
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1f,
                    this
                )
                if (::mMap.isInitialized) {
                    mMap.isMyLocationEnabled = true
                }
            }
        }
    }

    private fun toggleMapView(showHeadsUpView: Boolean) {
        if (!::mMap.isInitialized) return

        if (showHeadsUpView) {
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            mMap.uiSettings.isTiltGesturesEnabled = true
            currentLocationMarker?.position?.let {
                CameraPosition.builder()
                    .target(it)
                    .zoom(18f)
                    .tilt(45f)
                    .build()
            }?.let {
                CameraUpdateFactory.newCameraPosition(
                    it
                )
            }?.let {
                mMap.animateCamera(
                    it
                )
            }
        } else {
            mMap.uiSettings.isTiltGesturesEnabled = false
            currentLocationMarker?.position?.let {
                CameraPosition.builder()
                    .target(it)
                    .zoom(15f)
                    .tilt(0f)
                    .build()
            }?.let {
                CameraUpdateFactory.newCameraPosition(
                    it
                )
            }?.let {
                mMap.animateCamera(
                    it
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(this)
    }

    //This function will toggle the map view between a heads-up view (zoomed in and tilted) and a normal view. Update the onLocationChanged function to move the camera to the current location if the heads-up view is enabled:
    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        // Check if mMap is initialized
        if (!::mMap.isInitialized) return
        val altimeterSetting = 29.92 // example value, replace with actual value
        val airportElevation = 0 // example value, replace with actual value
        val pressureAltitudeInFeet =
            (altimeterSetting - location.altitude) * 1000 + airportElevation
        val fl = (pressureAltitudeInFeet / 100).roundToInt()
        var useMetricUnits = false
        val altitudeInFeet = if (useMetricUnits) location.altitude else location.altitude * 3.28084
        val altitudeDisplay = if (fl > 0) {
            "${altitudeInFeet.roundToInt()} ft (FL$fl)"
        } else {
            "${altitudeInFeet.roundToInt()} ft"
        }

        val locationString =
            "Location: ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}"
        val coordinates =
            "${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}"
        locationTextView.text = "$locationString\nAltitude: $altitudeDisplay"

        // Set click listener to copy coordinates to clipboard
        locationTextView.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Coordinates", coordinates)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        val speedUnit = if (useMetricUnits) "m/s" else "mph"
        speedTextView.text =
            "Speed: ${if (useMetricUnits) location.speed else location.speed * 2.23694} $speedUnit"

        val distanceUnit = if (useMetricUnits) "m" else "mi"
        distanceTextView.text =
            "Distance to target: ${"%.1f".format(if (useMetricUnits) distance else distance * 0.000621371)} $distanceUnit"

        val heading = location.bearingTo(targetLocation)
        val distance = location.distanceTo(targetLocation)
        headingTextView.text = "Heading: $heading°"
        distanceTextView.text =
            "Distance to target: ${"%.1f".format(if (useMetricUnits) distance else distance * 0.000621371)} $distanceUnit"

        val currentLatLng = LatLng(location.latitude, location.longitude)
        currentLocationMarker?.remove()
        currentLocationMarker =
            mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location"))

        // Update great circle line and move the camera if heads-up view is enabled
        val targetLatLng = LatLng(targetLocation.latitude, targetLocation.longitude)
        updateGreatCircleLine(currentLatLng, targetLatLng)
        if (mapToggle.isChecked) {
            val cameraPosition = CameraPosition.builder()
                .target(currentLatLng)
                .zoom(18f)
                .tilt(45f)
                .build()
            val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)
            mMap.animateCamera(cameraUpdate)
        }
    }

    private fun updateGreatCircleLine(currentLatLng: LatLng, targetLatLng: LatLng) {
        greatCircleLine?.remove()
        greatCircleLine = mMap.addPolyline(
            PolylineOptions()
                .add(currentLatLng, targetLatLng)
                .geodesic(true)
                .width(10f)
                .color(Color.BLUE)
        )
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Enable My Location layer
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions not granted yet, request them
            checkAndRequestPermissions()
        } else {
            // Permissions already granted, proceed with map initialization
            mMap.isMyLocationEnabled = true
        }

        // Add target location marker to map
        val targetLatLng = LatLng(targetLocation.latitude, targetLocation.longitude)
        targetLocationMarker =
            mMap.addMarker(MarkerOptions().position(targetLatLng).title("Target Location"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, 15f))
    }


}
