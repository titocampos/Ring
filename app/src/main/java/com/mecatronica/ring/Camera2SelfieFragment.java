/*
 * Reference: https://github.com/googlesamples/android-Camera2Basic
 */

package com.mecatronica.ring;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
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
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import com.mecatronica.ring.ui.DrawView;
import com.mecatronica.ring.ui.SignView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2SelfieFragment extends Fragment implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    private MediaPlayer mMediaPlayer;
    private static final String FRAGMENT_DIALOG = "dialog";
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final String TAG = Camera2SelfieFragment.class.getSimpleName();
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private String mCameraId;
    private AutoFitTextureView mTextureView;
    private DrawView mTargetView;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private int mState = STATE_PREVIEW;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private boolean mFlashSupported;
    private int mSensorOrientation;

    public static Camera2SelfieFragment newInstance() {
        return new Camera2SelfieFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2selfie, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.shutter).setOnClickListener(this);
        view.findViewById(R.id.back).setOnClickListener(this);

        mTextureView = view.findViewById(R.id.preview);

        mTargetView = view.findViewById(R.id.texture);
        mTargetView.post(new Runnable() {
            @Override
            public void run() {
                int left = (int) (mTargetView.getWidth() / 4f);
                int top = (int) (mTargetView.getHeight() / 3f);
                int right = (int) (3f * mTargetView.getWidth() / 4f);
                int bottom = (int) (2f * mTargetView.getHeight() / 3f);
                mTargetView.setStrokeWidth(3);
                mTargetView.setPosition(left, top, right, bottom);

                Log.d(TAG, "mTargetView: " + mTargetView.getBounds());
            }
        });

        signView = new SignView(getActivity());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.shutter: {
                takePicture();
                break;
            }
            case R.id.back: {
                Activity activity = getActivity();
                if (null != activity) {
                    activity.finish();
                }
                break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void takePicture() {
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(getContext(), R.raw.camera_click);
        }
        mMediaPlayer.start();
        Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (null != vibrator) {
            vibrator.vibrate(200);
        }
        lockFocus();
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            String imageFileName = "pic" + (new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
            File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM), "Camera");
            try {
                File file = File.createTempFile(
                        imageFileName,
                        ".jpg",
                        storageDir
                );
                mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), file));
                insertImageContent(getActivity().getApplicationContext().getContentResolver(), file,
                        mImageReader.getWidth(), mImageReader.getHeight());
                showToast("Saved: " + file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private void insertImageContent(ContentResolver contentResolver, File file, int width, int height) {
        long now = System.currentTimeMillis();
        final ContentValues values = new ContentValues(12);
        values.put(MediaStore.Images.Media.TITLE, file.getName());
        values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000);
        values.put(MediaStore.Images.Media.DATE_ADDED, now / 1000);
        values.put(MediaStore.Images.Media.DATA, file.getPath());
        values.put(MediaStore.Images.Media.SIZE, file.length());
        values.put(MediaStore.Images.Media.WIDTH, width);
        values.put(MediaStore.Images.Media.HEIGHT, height);
        values.put(MediaStore.Images.Media.DATE_TAKEN, now);

        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    processFace(result);
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {

                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    } else {
                        mAntiFreezeCount--;
                        if (mAntiFreezeCount <= 0) {
                            mState = STATE_PREVIEW;
                        } else {
                            break;
                        }
                    }
                    mAntiFreezeCount = FREEZE_COUNT;
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    } else {
                        mAntiFreezeCount--;
                        if (mAntiFreezeCount <= 0) {
                            mState = STATE_PREVIEW;
                        } else {
                            break;
                        }
                    }
                    mAntiFreezeCount = FREEZE_COUNT;
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    } else {
                        mAntiFreezeCount--;
                        if (mAntiFreezeCount <= 0) {
                            mState = STATE_PREVIEW;
                        } else {
                            break;
                        }
                    }
                    mAntiFreezeCount = FREEZE_COUNT;
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            //process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();

        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    StreamConfigurationMap map = characteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }

                    Size largest = null;
                    for (Size size : map.getOutputSizes(ImageFormat.JPEG)) {
                        if ((float) size.getWidth() / size.getHeight() == 16f/9) {
                            largest = size;
                            break;
                        }
                    }
                    if (largest == null) {
                        largest = Collections.max(
                                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                                new CompareSizesByArea());
                    }

                    WindowManager wm = activity.getWindowManager();
                    getWIdthsAndHeights(wm, largest);

                    mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                            ImageFormat.JPEG, 2);
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                    int displayRotation = wm.getDefaultDisplay().getRotation();

                    mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    boolean swappedDimensions = false;
                    switch (displayRotation) {
                        case Surface.ROTATION_0:
                        case Surface.ROTATION_180:
                            if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                                swappedDimensions = true;
                            }
                            break;
                        case Surface.ROTATION_90:
                        case Surface.ROTATION_270:
                            if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                                swappedDimensions = true;
                            }
                            break;
                        default:
                            Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                    }

                    Point displaySize = new Point();
                    activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                    int rotatedPreviewWidth = width;
                    int rotatedPreviewHeight = height;
                    int maxPreviewWidth = displaySize.x;
                    int maxPreviewHeight = displaySize.y;

                    if (swappedDimensions) {
                        rotatedPreviewWidth = height;
                        rotatedPreviewHeight = width;
                        maxPreviewWidth = displaySize.y;
                        maxPreviewHeight = displaySize.x;
                    }

                    if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                        maxPreviewWidth = MAX_PREVIEW_WIDTH;
                    }

                    if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                        maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                    }

                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                            maxPreviewHeight, largest);

                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }

                    mFlashSupported = false;

                    mCameraId = cameraId;

                    Log.d(TAG, "SENSOR_INFO_PIXEL_ARRAY_SIZE: " + characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).toString());
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (null == mCameraDevice) return;

                            mCaptureSession = cameraCaptureSession;
                            try {
                                mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                        CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                                setAutoFlash(mPreviewRequestBuilder);

                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void lockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();

            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private void unlockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private class ImageSaver implements Runnable {
        private final Image mImage;
        private final File mFile;

        private ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    public static class ConfirmationDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

   /*Tratamento faces******/
    private static int mCameraWidth;
    private static int mCameraHeight;
    private static int mScreenWidth;
    private static int mScreenHeight;
    private static final int OFFSET_X = 280;
    private static final int OFFSET_Y = -50;
    private static final int FREEZE_COUNT = 25;
    private static int mAntiFreezeCount = FREEZE_COUNT;
    private static long mLastFoundMillis = System.currentTimeMillis();
    private static final long TIMEOUT = 300;
    private static int mUpdateCounter = 0;
    private static final int THRESHOLD = 100;
    private static final int REFRESING_RATE = 6;
    private static int mCenteredCounter = 0;
    private static int CENTERED = 9;
    private Handler mUIHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<DrawView> mFaceRectList = new ArrayList<>();
    private final Object mComputeFaceLock = new Object();
    private SignView signView;
    private boolean mMatched;

    private synchronized void runOnUiThread(Runnable r) {
        if (mUIHandler != null) mUIHandler.post(r);
    }

    private void getWIdthsAndHeights(WindowManager wm, Size largest) {
        Log.d(TAG, "jpegWidth: " + largest.getWidth() + ", jpegHeight: " + largest.getHeight());
        mCameraWidth = largest.getWidth();
        mCameraHeight = largest.getHeight();
        Point point = new Point();
        wm.getDefaultDisplay().getRealSize(point);
        mScreenWidth = point.x;
        mScreenHeight = point.y;
        Log.d(TAG, "screenWidth: " + point.x + ", screenHeight: " + point.y);
    }
long i = 0;
    private void processOutBounds(Face[] faces){
        if (mCameraWidth == 0 || mCameraHeight == 0 || mScreenHeight == 0 || mScreenWidth == 0)
            return;

        Rect outBounds= new Rect(mCameraWidth, mCameraHeight, 0, 0);
        for (Face face : faces){
            Rect faceBounds = face.getBounds();

            if (outBounds.left > faceBounds.left) outBounds.left = faceBounds.left;
            if (outBounds.top > faceBounds.top) outBounds.top = faceBounds.top;
            if (outBounds.right < faceBounds.right) outBounds.right = faceBounds.right;
            if (outBounds.bottom < faceBounds.bottom) outBounds.bottom = faceBounds.bottom;
        }
        Rect targetCenter = screenToCameraCoord(mTargetView.getBounds());

        if (i++ % 10 == 0) {
            Log.d(TAG, "outBounds: " + outBounds.toString());
            Log.d(TAG, "outBounds.XY: Point(" + outBounds.exactCenterX() + ", " + outBounds.exactCenterY() + ")");
            Log.d(TAG, "targetCenter.XY: Point(" + targetCenter.exactCenterX() + ", " + targetCenter.exactCenterY() + ")");
            Log.d(TAG, "centerDistance: " + centerDistance(outBounds, targetCenter));
            Log.d(TAG, "**");
            Log.d(TAG, "setSignalingLeft: " + (outBounds.exactCenterX() / (targetCenter.exactCenterX())));
            Log.d(TAG, "setSignalingRight: " + (outBounds.exactCenterX() / (targetCenter.exactCenterX())));
            Log.d(TAG, "setSignalingTop: " + (outBounds.exactCenterY() / (targetCenter.exactCenterY())));
            Log.d(TAG, "setSignalingBottom: " + (outBounds.exactCenterY() / (targetCenter.exactCenterY())));
            Log.d(TAG, "*********************************************************************************");
        }

        mMatched = centerDistance(outBounds, targetCenter) < THRESHOLD;
        if (!mMatched) {
            signView.setSignalingLeft(outBounds.exactCenterY() / (targetCenter.exactCenterY()) < 0.98f);
            signView.setSignalingRight(outBounds.exactCenterY() / (targetCenter.exactCenterY()) > 1.02f);
            signView.setSignalingTop(outBounds.exactCenterX() / (targetCenter.exactCenterX()) < 0.98f);
            signView.setSignalingBottom(outBounds.exactCenterX() / (targetCenter.exactCenterX()) > 1.02f);
        }
        signView.setRect(cameraToScreenCoord(outBounds));
    }

    private double centerDistance(Rect f, Rect t) {
        float x1 = t.exactCenterX();
        float x2 = f.exactCenterX();
        float y1 = t.exactCenterY();
        float y2 = f.exactCenterY();

        return Math.sqrt(((x1 - x2) * (x1 - x2)) + ((y1 - y2) * (y1 - y2)));
    }

    private void processFace(CaptureResult result) {
        Integer mode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
        final Face[] faces = result.get(CaptureResult.STATISTICS_FACES);

        if (faces != null && mode != null && faces.length >= 1) {
            mLastFoundMillis = System.currentTimeMillis();

            if (mUpdateCounter >= REFRESING_RATE) {
                mMatched = false;
                processOutBounds(faces);

                if (mMatched) {
                    mCenteredCounter++;

                    if (mCenteredCounter == CENTERED - 3) {
                        signView.Flash();
                    } else if (mCenteredCounter >= CENTERED) {
                        mCenteredCounter = 0;
                        takePicture();
                        CENTERED = 15;
                        signView.Clear();
                    }
                } else {
                    mCenteredCounter = 0;
                }
                if (mCenteredCounter < CENTERED - 3) {
                    signView.Sinalize();
                }
                mUpdateCounter = 0;
            } else {
                mUpdateCounter++;
            }
        } else if (System.currentTimeMillis() - mLastFoundMillis > TIMEOUT) {
            signView.Clear();
            mLastFoundMillis = System.currentTimeMillis();
            mCenteredCounter = 0;
            CENTERED = 5;
        }

        if (faces == null || faces.length < mFaceRectList.size())
            clearFaceRects();

        if (faces != null && mode != null) {
            ArrayList<Rect> boundList = new ArrayList<>();
            for (Face face : faces)
                boundList.add(cameraToScreenCoord(face.getBounds()));
            updateFaceRectList(boundList);
        }

        drawFaceRects();
    }

    private Rect screenToCameraCoord(Rect rect) {
        int left   = (int) ((rect.top - OFFSET_Y)    / ((float) mScreenHeight / mCameraWidth));
        int top    = mCameraHeight - (int) ((rect.left - OFFSET_X) / ((float) mScreenWidth / mCameraHeight));
        int right  = (int) ((rect.bottom - OFFSET_Y) / ((float) mScreenHeight / mCameraWidth));
        int bottom = mCameraHeight - (int) ((rect.right - OFFSET_X) / ((float) mScreenWidth / mCameraHeight));
        return new Rect(left, top, right, bottom);
    }

    private Rect cameraToScreenCoord(Rect rect) {
        int left = (int) (((float) mCameraHeight - rect.top) * mScreenWidth / mCameraHeight) + OFFSET_X;
        int top = (int) ((float) rect.left * mScreenHeight / mCameraWidth) + OFFSET_Y;
        int right = (int) (((float) mCameraHeight - rect.bottom) * mScreenWidth / mCameraHeight) + OFFSET_X;
        int bottom = (int) ((float) rect.right * mScreenHeight / mCameraWidth) + OFFSET_Y;
        return new Rect(left, top, right, bottom);
    }

    private void updateFaceRectList(List<Rect> screenBoundsList) {
        for (int i = 0; i < screenBoundsList.size(); i++) {
            DrawView faceRect;
            if (mFaceRectList.size() <= i) {
                faceRect = new DrawView(getActivity(), 0);
                mFaceRectList.add(faceRect);
            } else {
                faceRect = mFaceRectList.get(i);
            }

            Rect screenBounds = screenBoundsList.get(i);
            if (null != faceRect) {
                faceRect.setPosition(screenBounds);
                faceRect.setColor("#ffdd00");
                faceRect.setFocusable(false);
            }
        }

        int n1 = screenBoundsList.size();
        int n2 = mFaceRectList.size();
        while (n1 < n2) {
            n2--;
            //Log.d(TAG, "screenBoundsList " + n1 + ", mFaceRectList: " + n2);
            if (n2 < mFaceRectList.size())
                removeFaceRect(mFaceRectList.get(n2));
        }
    }

    private void drawFaceRects() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup root = (ViewGroup) mTextureView.getRootView();
                for (View v : mFaceRectList) {
                    if (!v.getRootView().equals(root))
                        root.addView(v);
                    else
                        v.invalidate();
                }

                if (!signView.getRootView().equals(root))
                    root.addView(signView);
                else
                    signView.invalidate();
            }
        });
    }

    private void removeFaceRect(final DrawView v) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup root = (ViewGroup) mTextureView.getRootView();
                root.removeView(v);
                mFaceRectList.remove(v);
            }
        });
    }

    private void clearFaceRects() {
        if (mFaceRectList.size() == 0) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup root = (ViewGroup) mTextureView.getRootView();
                for (DrawView v : mFaceRectList) {
                    root.removeView(v);
                }
                mFaceRectList.clear();
            }
        });
    }
}
