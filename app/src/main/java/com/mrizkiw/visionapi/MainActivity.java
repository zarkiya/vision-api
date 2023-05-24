package com.mrizkiw.visionapi;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSION = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private static final String API_URL = "https://asia-southeast2-firstproj-382606.cloudfunctions.net/ImageClassificationNewest";

    private ProgressBar progressBar;
    private ImageView imageView;
    private Button uploadButton;
    private Button selectButton;
    private Button downloadButton;
    private String selectedImageFilename;
    private Bitmap selectedImageBitmap;
    private String selectedAnnotatedImageUrl;
    private TextView title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        uploadButton = findViewById(R.id.uploadButton);
        selectButton = findViewById(R.id.selectButton);
        downloadButton = findViewById(R.id.downloadButton);
        progressBar = findViewById(R.id.progressBar);
        title = findViewById(R.id.title);

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImage();
            }
        });

        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectImageFromGallery();
            }
        });

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadImage();
            }
        });
    }

    private void selectImageFromGallery() {
        // Check if the READ_EXTERNAL_STORAGE permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Request the permission if it is not granted
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        } else {
            // Permission is already granted, open the image picker
            openImagePicker();
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, open the image picker
            openImagePicker();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            // Get the selected image URI
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    // Load the selected image into ImageView
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                    imageView.setImageBitmap(bitmap);

                    selectedImageFilename = getFilenameFromUri(imageUri);

                    // Show toast notification
                    Toast.makeText(MainActivity.this, "Image selected", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getFilenameFromUri(Uri uri) {
        String filename = null;
        Cursor cursor = null;
        try {
            String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                filename = cursor.getString(columnIndex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return filename;
    }

    private void uploadImage() {
        // Get the selected image from ImageView
        Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

        // Convert image to base64
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        String imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT);

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("image", imageBase64);
            jsonObject.put("filename", selectedImageFilename);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonObject.toString());

        // Create HTTP request
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        // Show progress bar
        progressBar.setVisibility(View.VISIBLE);

        // Execute the request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();

                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Log.d("Response", json);

                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        final String annotatedImageUrl = jsonObject.optString("annotatedImageUrl");
                        final String label = jsonObject.optString("label");

                        // Show toast notification
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Hide progress bar
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(MainActivity.this, "Response received", Toast.LENGTH_SHORT).show();
                                // Show label
                                title.setText("Label: " + label);
                            }
                        });

                        // Enable the download button
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                downloadButton.setEnabled(true);
                            }
                        });

                        // Store the annotated image URL for downloading
                        selectedAnnotatedImageUrl = annotatedImageUrl;

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Hide progress bar
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Failed to receive response", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            }
        });
    }

    private void downloadImage() {
        // Check if the annotated image URL is available
        if (selectedAnnotatedImageUrl != null && !selectedAnnotatedImageUrl.isEmpty()) {
            // Create a DownloadManager instance
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

            // Create a DownloadManager.Request with the URL
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(selectedAnnotatedImageUrl));

            // Set the destination directory and filename
            String filename = "annotated_image.png";
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            // Enqueue the download and get the download ID
            long downloadId = downloadManager.enqueue(request);

            // Show toast notification
            Toast.makeText(MainActivity.this, "Download started", Toast.LENGTH_SHORT).show();

            // Register a BroadcastReceiver to listen for the completion of the download
            final long finalDownloadId = downloadId;
            BroadcastReceiver onCompleteReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (completedDownloadId == finalDownloadId) {
                        // Download completed, show toast notification
                        Toast.makeText(MainActivity.this, "Download completed", Toast.LENGTH_SHORT).show();
                    }
                }
            };

            registerReceiver(onCompleteReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } else {
            // Show toast notification if annotated image URL is not available
            Toast.makeText(MainActivity.this, "Annotated image URL is not available", Toast.LENGTH_SHORT).show();
        }
    }

}