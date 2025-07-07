# Activity Recognition Sample

This is a sample project demonstrating how to use Google's Activity Recognition API for Android. This API allows your application to detect the user's current physical activity, such as walking, running, cycling, or driving.

## Features

*   Detects the user's current physical activity.
*   (Optional, if you've added map functionality) Displays the user's location and activity on a map.

## Setup Instructions

### 1. Google Maps API Key (If using Maps)

If your project integrates Google Maps to display location information, you need to obtain a Google Maps API key and add it to your `AndroidManifest.xml` file. Otherwise, the map functionality will not work.

1.  **Get an API Key:**
    *   Go to the [Google Cloud Console](https://console.cloud.google.com/).
    *   Create a new project or select an existing one.
    *   Navigate to **APIs & Services > Credentials**.
    *   Click **Create Credentials** and select **API key**.
    *   Once the API key is created, **restrict the key** to be used only by your Android app. You'll need your app's package name and SHA-1 certificate fingerprint.
    *   Enable the **Maps SDK for Android** for your project if it's not already enabled.

2.  **Add the API Key to your `AndroidManifest.xml`:**

    Open your `app/src/main/AndroidManifest.xml` file. Locate the existing `<meta-data>` tag with `android:name="com.google.android.geo.API_KEY"` inside the `<application>` tag. **Replace** the `android:value` in that tag with the API key you obtained:

    