package com.rlopez.molecare.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.rlopez.molecare.R;
import com.rlopez.molecare.configuration.Configuration;
import com.rlopez.molecare.utils.AutoFitTextureView;
import com.rlopez.molecare.images.ImageProcessor;
import com.rlopez.molecare.utils.FileManager;
import com.rlopez.molecare.images.ImageModel;
import com.rlopez.molecare.images.ImagesInformation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private Size imageDimensions;
    private AutoFitTextureView cameraPreview;
    private ImageView squareView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession captureSession;
    private CameraCharacteristics characteristics;
    private File moleFolder;
    private int trimDimension;
    private ImagesInformation imagesInformation;

    private File photoFile;

    ImageView captureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        squareView = findViewById(R.id.cameraSquareView);

        // Get extras
        String configurationFilePath = getIntent().getStringExtra("CONFIGURATION_FILE_PATH");
        String folderPath = getIntent().getStringExtra("PATH");
        String moleName = getIntent().getStringExtra("MOLE_NAME");

        // Get trim dimension from configuration
        assert configurationFilePath != null;
        Configuration configuration = Configuration.readConfigurationJSON(new File(configurationFilePath), getApplicationContext());
        trimDimension = Integer.parseInt(configuration.getImageConfiguration().getTrimDimension());

        // Get mole folder
        if (moleName != null) {
            moleFolder = new File(folderPath, moleName);
        } else {
            assert folderPath != null;
            moleFolder = new File(folderPath);
        }

        // Set a listener for the camera preview
        cameraPreview = findViewById(R.id.textureView);
        cameraPreview.setSurfaceTextureListener(surfaceTextureListener);

        // If capture button is pressed, take a photo
        captureButton = findViewById(R.id.shutter_button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureButton.setImageDrawable(getDrawable(R.drawable.shutter_button));
                takePhoto();
            }
        });

    }

    // Take the photo
    private void takePhoto() {
        Toast.makeText(getApplicationContext(), R.string.photo_processing, Toast.LENGTH_LONG).show();
        if (cameraDevice == null) {
            return;
        }
        try {
            // Create an image reader with max resolution to take a JPEG picture
            ImageReader reader = ImageReader.newInstance(imageDimensions.getWidth(), imageDimensions.getHeight(), ImageFormat.JPEG, 1);

            // List of surfaces for a new capture session (later in code)
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(cameraPreview.getSurfaceTexture()));

            // Capture builder to build a single capture
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Set the listener for the image reader
            ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // Get the last image bytes and save it into a file
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } finally {
                        if (image != null)
                            image.close();
                    }
                }

                // Save a bytes array into a file
                void save(byte[] bytes) {
                    // Save bytes to the new photo file
                    OutputStream outputStream = null;
                    File tempPhotoFile = null;
                    try {
                        tempPhotoFile = FileManager.createTempPhotoFile(moleFolder.getAbsolutePath());
                        outputStream = new FileOutputStream(tempPhotoFile);
                        outputStream.write(bytes);
                    } catch (IOException e) {
                        Toast.makeText(getApplicationContext(), R.string.error_creating_file, Toast.LENGTH_SHORT).show();
                    } finally {
                        try {
                            if (outputStream != null) {
                                outputStream.close();
                            }
                            // Trim the image and rotate it if needed
                            Bitmap preProcessedPhoto = ImageProcessor.cropAndRotatePhoto(tempPhotoFile, trimDimension);
                            try (FileOutputStream out = new FileOutputStream(photoFile)) {
                                preProcessedPhoto.compress(Bitmap.CompressFormat.PNG, 100, out);
                                tempPhotoFile.delete();
                                Toast.makeText(getApplicationContext(), R.string.photo_saved, Toast.LENGTH_SHORT).show();
                                CameraActivity.this.finish();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        } catch (IOException e) {
                            Toast.makeText(getApplicationContext(), R.string.error_creating_file, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            };

            // New thread to handle picture taking
            HandlerThread handlerThread = new HandlerThread("Take picture handler");
            handlerThread.start();
            final Handler handler = new Handler(handlerThread.getLooper());
            reader.setOnImageAvailableListener(imageAvailableListener, handler);

            // Handle capture started and completed
            final CameraCaptureSession.CaptureCallback previewSession = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    // If creating a new mole, create needed folder
                    if (!moleFolder.exists()) {
                        moleFolder.mkdirs();
                    }

                    // Create the photo file with current timestamp
                    try {
                        photoFile = FileManager.createTempPhotoFile(moleFolder.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Save the focus used to take the photo into a JSON file
                    File imagesInformationFile = new File(moleFolder.getAbsolutePath(), "ImagesInformation.json");
                    ImageModel imageModel = new ImageModel(photoFile.getName(), String.valueOf(result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
                    Gson gson = new Gson();
                    if (!imagesInformationFile.exists()) {
                        try {
                            List<ImageModel> imageModelList = new ArrayList<>();
                            imageModelList.add(imageModel);
                            imagesInformation = new ImagesInformation(imageModelList);
                            String imagesInformationJSON = gson.toJson(imagesInformation);

                            // Create the new JSON file
                            FileWriter JSONWriter = new FileWriter(imagesInformationFile);
                            JSONWriter.append(imagesInformationJSON);
                            JSONWriter.flush();
                            JSONWriter.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            imagesInformation = ImagesInformation.readImagesInformationJSON(imagesInformationFile, getApplicationContext());
                            imagesInformation.addImageModel(imageModel);
                            String imagesInformationJSON = gson.toJson(imagesInformation);

                            // Update the JSON file
                            FileWriter JSONWriter = new FileWriter(imagesInformationFile);
                            JSONWriter.write(imagesInformationJSON);
                            JSONWriter.flush();
                            JSONWriter.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), previewSession, handler);
                    } catch (Exception e) {
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, handler);
        } catch (CameraAccessException e) {
            Toast.makeText(getApplicationContext(), R.string.error_access_camera, Toast.LENGTH_SHORT).show();
        }
    }

    // Open back camera with max resolution
    public void openCamera() {
        // Get camera service
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Get back camera and its characteristics
            String cameraId = manager.getCameraIdList()[0];
            characteristics = manager.getCameraCharacteristics(cameraId);

            // Check permissions and get back if necessary
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                CameraActivity.this.finish();
                return;
            }
            // Open back camera
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(getApplicationContext(), R.string.error_access_camera, Toast.LENGTH_SHORT).show();
        }

        // Informative dialog
        AlertDialog alertDialog = new AlertDialog.Builder(CameraActivity.this).create();
        alertDialog.setTitle(R.string.photo_instructions_title);
        alertDialog.setMessage(getString(R.string.photo_instructions));
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    // Configure the listener for the camera preview
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // When surface is available open the camera
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Not used
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Not used
        }
    };

    // Manage camera device callbacks
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            // When camera device is opened, get it and start it
            cameraDevice = camera;
            startCamera();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            // Not used
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            // Not used
        }
    };

    @Override
    protected void onPause() {
        super.onPause();

        // Close camera device and get back
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        CameraActivity.this.finish();
    }

    // Start the camera
    void startCamera() {
        if (cameraDevice == null || !cameraPreview.isAvailable()) {
            return;
        }

        // Get the texture from the preview
        SurfaceTexture texture = cameraPreview.getSurfaceTexture();
        if (texture == null) {
            return;
        }

        // Set buffer size and preview aspect ratio
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        imageDimensions = map.getOutputSizes(SurfaceTexture.class)[0];
        if (imageDimensions.getHeight() > imageDimensions.getWidth()) {
            texture.setDefaultBufferSize(imageDimensions.getHeight(), imageDimensions.getWidth());
            cameraPreview.setAspectRatio(imageDimensions.getWidth(), imageDimensions.getHeight());
        } else {
            texture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());
            cameraPreview.setAspectRatio(imageDimensions.getHeight(), imageDimensions.getWidth());
        }

        // Set central square dimension
        /*int dimensionFactor = (imageDimensions.getHeight() * imageDimensions.getWidth()) / (cameraPreview.getHeight() * cameraPreview.getWidth());
        int newSquareSideDimension = (trimDimension / dimensionFactor);
        squareView.getLayoutParams().width = newSquareSideDimension;
        squareView.getLayoutParams().height = newSquareSideDimension;*/

        // Set central square adjusted dimension
        int imagePixels = imageDimensions.getHeight() * imageDimensions.getWidth();
        int previewPixels = cameraPreview.getHeight() * cameraPreview.getWidth();
        int trimPixels = trimDimension*trimDimension;
        int adjustedTrimPixels = previewPixels / (imagePixels/trimPixels);
        squareView.getLayoutParams().width = (int) Math.round(Math.sqrt(adjustedTrimPixels));
        squareView.getLayoutParams().height = (int) Math.round(Math.sqrt(adjustedTrimPixels));

        // Get the surface and attach it to the camera request builder
        Surface surface = new Surface(texture);
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            Toast.makeText(getApplicationContext(), R.string.error_access_camera, Toast.LENGTH_SHORT).show();
        }
        captureRequestBuilder.addTarget(surface);

        // Create a capture session and manage configuration
        try {
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    getChangedPreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, null);
        } catch (CameraAccessException e) {
            Toast.makeText(getApplicationContext(), R.string.error_access_camera, Toast.LENGTH_SHORT).show();
        }
    }

    // Manage preview change (configured)
    void getChangedPreview() {
        if (cameraDevice == null) {
            return;
        }

        // Set capture request builder control mode to auto
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        // Create a new thread to handle background operations
        HandlerThread thread = new HandlerThread("Background thread");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
        } catch (CameraAccessException e) {
            Toast.makeText(getApplicationContext(), R.string.error_access_camera, Toast.LENGTH_SHORT).show();
        }
    }

}