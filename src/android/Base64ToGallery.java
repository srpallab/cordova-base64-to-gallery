package it.nexxa.base64ToGallery;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.Manifest;  // For permission handling

/**
 * Base64ToGallery.java
 *
 * Android implementation of the Base64ToGallery for iOS.
 * Inspired by Joseph's "Save HTML5 Canvas Image to Gallery" plugin
 * http://jbkflex.wordpress.com/2013/06/19/save-html5-canvas-image-to-gallery-phonegap-android-plugin/
 *
 * @author Vegard Løkken <vegard@headspin.no>
 */
public class Base64ToGallery extends CordovaPlugin {

    // Consts
    public static final String EMPTY_STR = "";

    // Constructor
    public Base64ToGallery() {}

    @Override
    public boolean execute(String action, JSONArray args,
                           CallbackContext callbackContext) throws JSONException {

        String base64 = args.optString(0);
        String filePrefix = args.optString(1);
        boolean mediaScannerEnabled = args.optBoolean(2);

        // isEmpty() requires API level 9
        if (base64.equals(EMPTY_STR)) {
            callbackContext.error("Missing base64 string");
        }

        // Create the bitmap from the base64 string
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        if (bmp == null) {
            callbackContext.error("The image could not be decoded");

        } else {
            // Save the image
            File imageFile = savePhoto(bmp, filePrefix);

            if (imageFile == null) {
                callbackContext.error("Error while saving image");
            }

            // Update image gallery
            if (mediaScannerEnabled && imageFile != null) {
                scanPhoto(imageFile);
            }

            callbackContext.success(imageFile.toString());
        }

        return true;
    }

    private File savePhoto(Bitmap bmp, String prefix) {
        File retVal = null;

        try {
            String date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File folder;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+ (Scoped Storage)
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, prefix + date + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                Uri uri = cordova.getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream outputStream = cordova.getContext().getContentResolver().openOutputStream(uri);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    outputStream.close();
                }
            } else {
                // Pre-Android 10, use traditional file saving methods
                folder = cordova.getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                File imageFile = new File(folder, prefix + date + ".png");
                FileOutputStream out = new FileOutputStream(imageFile);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();
                retVal = imageFile;
            }

        } catch (Exception e) {
            Log.e("Base64ToGallery", "An exception occurred while saving the image: " + e.toString());
        }

        return retVal;
    }

    /**
     * Invoke the system's media scanner to add your photo to the Media Provider's database,
     * making it available in the Android Gallery application and to other apps.
     */
    private void scanPhoto(File imageFile) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageFile);
        mediaScanIntent.setData(contentUri);
        cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    /**
     * Request permission if needed (Android 6.0+)
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (cordova.getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                cordova.requestPermissions(this, 0, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
            }
        }
    }
}