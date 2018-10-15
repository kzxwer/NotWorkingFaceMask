package com.android.facemask.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.CameraSource;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sumit on 26/6/17.
 */
public class CameraSourcePreview extends ViewGroup {
    private int filterStartX, filterStartY;
    private static final int FILTERED_PREVIEW_SIZE = 96;
    private static final int FILTER_PREVIEWS_PER_ROW = 3;
    private int margin;
    private GLDrawer2D mDrawer;
    private final WeakReference<CameraSourcePreview> mWeakParent;
    private final float[] mMvpMatrix = new float[16];
    protected int mVideoWidth, mVideoHeight;
    private final Queue<Runnable> mRunOnDraw = new LinkedList<>();
    private List<GLDrawer2D> filterPreviews = new ArrayList<>();

    int mScaleType = ScaleType.SCALE_SQUARE;
    int mMediaType= MediaType.IMAGE;

    private static final String TAG = "CameraSourcePreview";

    private Context mContext;
    private SurfaceView mSurfaceView;
    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private CameraSource mCameraSource;
    private int filterPreviewSize;
    private int screenHeight, screenWidth;
    private GraphicOverlay mOverlay = null;


    public CameraSourcePreview(Context context, AttributeSet attrs, final CameraSourcePreview parent) {
        super(context, attrs);
        mContext = context;
        mStartRequested = false;
        mSurfaceAvailable = false;

        mSurfaceView = new SurfaceView(context);
        mWeakParent = new WeakReference<>(parent);
        Matrix.setIdentityM(mMvpMatrix, 0);
        mDrawer = new GLDrawer2D();
        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();

        screenHeight = displayMetrics.heightPixels;
        screenWidth = displayMetrics.widthPixels;
        filterPreviewSize = (int) (FILTERED_PREVIEW_SIZE * displayMetrics.density);

        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        addView(mSurfaceView);
    }

    private void setFilterPreviewEnabled(boolean enabled) {
        if (!enabled) {
            for (GLDrawer2D drawer : filterPreviews) {
                drawer.release();
            }

            filterPreviews.clear();
        }
    }
    public void createFilters() {
        mDrawer.init();
        mDrawer.setMatrix(mMvpMatrix, 0);


        Log.d(TAG, "createFilterPreviews");

        addFilter(mDrawer.createCopy());
        addFilter(new GLGrayscaleFilter());
        addFilter(new GLColorInvertFilter());
        addFilter(new GLSepiaFilter());
    }

    public void addFilter(GLDrawer2D drawer) {
        drawer.init();
        drawer.setMatrix(mMvpMatrix, 0);
        filterPreviews.add(drawer);
    }
    public void start(CameraSource cameraSource) throws IOException {
        if (cameraSource == null) {
            stop();
        }

        mCameraSource = cameraSource;

        if (mCameraSource != null) {
            mStartRequested = true;
            startIfReady();
        }
    }
    private void onFilterSelected(GLDrawer2D filter) {
        Log.d(TAG, "onFilterSelected : " + filter.getClass().getSimpleName());

        runOnDraw(() -> {
            mDrawer.release();
            mDrawer = filter.createCopy();

            mDrawer.init();
            mDrawer.setMatrix(mMvpMatrix, 0);

            updateFiltersUI();
            updateViewport();
        });
    }
    private void updateViewport() {
        final CameraSourcePreview parent = mWeakParent.get();
        if (parent == null) return;

        final int view_width = parent.getWidth();
        final int view_height = parent.getHeight();

        final double video_width = 320;
        final double video_height = 240;

        if (view_width == 0 || view_height == 0 || video_width == 0 || video_height == 0) {
            Log.e(TAG, "updateViewport: view: width: " + view_width + " height: " + view_height + " video: width: " + video_width + " height: " + video_height);
            return;
        }

        Log.d(TAG, "glViewport: updateViewport: (0, 0, " + view_width + "," + view_height + ")");

        GLES20.glViewport(0, 0, view_width, view_height);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        Matrix.setIdentityM(mMvpMatrix, 0);
        final double view_aspect = view_width / (double) view_height;

        Log.i(TAG, String.format("updateViewport: view: (%d,%d) view_aspect: %f,video: (%1.0f,%1.0f)",
                view_width, view_height, view_aspect, video_width, video_height));

        mDrawer.setRect(0, 0, view_width, view_height);

        switch (parent.mScaleType) {
            case ScaleType.SCALE_STRETCH_FIT:
                break;
            case ScaleType.SCALE_KEEP_ASPECT_VIEWPORT: {
                final double req = video_width / video_height;
                int x, y;
                int width, height;
                if (view_aspect > req) {
                    // if view is wider than camera image, calc width of drawing area based on view height
                    y = 0;
                    height = view_height;
                    width = (int) (req * view_height);
                    x = (view_width - width) / 2;
                } else {
                    // if view is higher than camera image, calc height of drawing area based on view width
                    x = 0;
                    width = view_width;
                    height = (int) (view_width / req);
                    y = (view_height - height) / 2;
                }
                // set viewport to draw keeping aspect ration of camera image
                Log.d(TAG, String.format("xy(%d,%d),size(%d,%d)", x, y, width, height));

                Log.d(TAG, "glViewport: (" + x + ", " + y + ", " + width + "," + height + ")");

                GLES20.glViewport(x, y, width, height);
                break;
            }
            case ScaleType.SCALE_KEEP_ASPECT:
            case ScaleType.SCALE_CROP_CENTER: {
                final double scale_x = view_width / video_width;
                final double scale_y = view_height / video_height;
                final double scale =
                        (parent.mScaleType == ScaleType.SCALE_CROP_CENTER ? Math.max(scale_x, scale_y)
                                : Math.min(scale_x, scale_y));
                final double width = scale * video_width;
                final double height = scale * video_height;

                Log.d(TAG,
                        String.format("size(%1.0f,%1.0f),scale(%f,%f),mat(%f,%f)", width, height, scale_x,
                                scale_y, width / view_width, height / view_height));

                Matrix.scaleM(mMvpMatrix, 0, (float) (width / view_width),
                        (float) (height / view_height), 1.0f);
                break;
            }
            case ScaleType.SCALE_SQUARE: {
                int view_x = 0;
                int view_y = 0;
                float scale_x = 1;
                float scale_y = 1;
                final int newPreviewSize;

                if (view_width >= view_height) {
                    newPreviewSize = view_height;
                    view_x = (view_width - newPreviewSize) / 2;

                } else {
                    newPreviewSize = view_width;
                    view_y = ((view_height - newPreviewSize) * 2)/3;
                }

                final float video_aspect = (float) (video_width / video_height);
                if (video_aspect >= 1) {
                    scale_x = video_aspect;
                } else {
                    scale_y = 1 / video_aspect;
                }
                Log.v(TAG, "scale square: " + scale_x + " " + scale_y + " (x,y) view_x : " + view_x + " view_y : " + view_y);

                GLES20.glViewport(view_x, view_y, newPreviewSize, newPreviewSize);
                Log.d(TAG, "glViewport: (" + view_x + ", " + view_y + ", " + newPreviewSize + "," + newPreviewSize + ")");

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                mDrawer.setRect(view_x, view_y, newPreviewSize, newPreviewSize);
                Matrix.scaleM(mMvpMatrix, 0, scale_x, scale_y, 1.0f);
                break;
            }
        }

        if (mDrawer != null) {
            mDrawer.setMatrix(mMvpMatrix, 0);
        }
    }

    protected void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.add(runnable);
        }
    }


    public void touched(String name) {
        if (name.equals("none")){
            onFilterSelected(filterPreviews.get(0));
        }
        if (name.equals("bw")){
            onFilterSelected(filterPreviews.get(1));
        }else if(name.equals("negative")){
            onFilterSelected(filterPreviews.get(2));
        }else if(name.equals("sepia")){
            onFilterSelected(filterPreviews.get(3));
        }


    }



    public void start(CameraSource cameraSource, GraphicOverlay overlay) throws IOException {
        mOverlay = overlay;
        start(cameraSource);
    }

    public void stop() {
        if (mCameraSource != null) {
            mCameraSource.stop();
        }
    }

    public void release() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    private void startIfReady() throws IOException {
        if (mStartRequested && mSurfaceAvailable) {
            mCameraSource.start(mSurfaceView.getHolder());
            if (mOverlay != null) {
                Size size = mCameraSource.getPreviewSize();
                int min = Math.min(size.getWidth(), size.getHeight());
                int max = Math.max(size.getWidth(), size.getHeight());
                if (isPortraitMode()) {
                    // Swap width and height sizes when in portrait, since it will be rotated by
                    // 90 degrees
                    mOverlay.setCameraInfo(min, max, mCameraSource.getCameraFacing());
                } else {
                    mOverlay.setCameraInfo(max, min, mCameraSource.getCameraFacing());
                }
                mOverlay.clear();
            }
            mStartRequested = false;
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            mSurfaceAvailable = true;
            try {
                startIfReady();
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            mSurfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int previewWidth = 320;
        int previewHeight = 240;
        if (mCameraSource != null) {
            Size size = mCameraSource.getPreviewSize();
            if (size != null) {
                previewWidth = size.getWidth();
                previewHeight = size.getHeight();
            }
        }

        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode()) {
            int tmp = previewWidth;
            previewWidth = previewHeight;
            previewHeight = tmp;
        }

        final int viewWidth = right - left;
        mVideoWidth = viewWidth;
        final int viewHeight = bottom - top;
        mVideoHeight = viewHeight;

        int childWidth;
        int childHeight;
        int childXOffset = 0;
        int childYOffset = 0;
        float widthRatio = (float) viewWidth / (float) previewWidth;
        float heightRatio = (float) viewHeight / (float) previewHeight;

        // To fill the view with the camera preview, while also preserving the correct aspect ratio,
        // it is usually necessary to slightly oversize the child and to crop off portions along one
        // of the dimensions.  We scale up based on the dimension requiring the most correction, and
        // compute a crop offset for the other dimension.
        if (widthRatio > heightRatio) {
            childWidth = viewWidth;
            childHeight = (int) ((float) previewHeight * widthRatio);
            childYOffset = (childHeight - viewHeight) / 2;
        } else {
            childWidth = (int) ((float) previewWidth * heightRatio);
            childHeight = viewHeight;
            childXOffset = (childWidth - viewWidth) / 2;
        }

        for (int i = 0; i < getChildCount(); ++i) {
            // One dimension will be cropped.  We shift child over or up by this offset and adjust
            // the size to maintain the proper aspect ratio.
            getChildAt(i).layout(
                    -1 * childXOffset, -1 * childYOffset,
                    childWidth - childXOffset, childHeight - childYOffset);
        }

        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        screenHeight = displayMetrics.heightPixels;
        screenWidth = displayMetrics.widthPixels;
        filterPreviewSize = (int) (FILTERED_PREVIEW_SIZE * displayMetrics.density);

        margin = (screenWidth - (filterPreviewSize * FILTER_PREVIEWS_PER_ROW)) / (FILTER_PREVIEWS_PER_ROW + 1);

        filterStartX = margin;
        filterStartY = (int) (0.20 * screenHeight);
        createFilters();

        try {
            startIfReady();
        } catch (IOException e) {
            Log.e(TAG, "Could not start camera source.", e);
        }
    }

    private void updateFiltersUI() {
        final CameraSourcePreview parent = mWeakParent.get();
        if (parent == null) return;

        final double video_width = parent.mVideoWidth;
        final double video_height = parent.mVideoHeight;

        if (video_width == 0 || video_height == 0) {
            Log.e(TAG, "updateFiltersUI: " + video_width + " videoheight: " + video_height);
            return;
        }

        final double scale_x = filterPreviewSize / video_width;
        final double scale_y = filterPreviewSize / video_height;
        final double scale = Math.max(scale_x, scale_y);

        final double width = scale * video_width;
        final double height = scale * video_height;

        int startX = filterStartX;
        int startY = filterStartY;

        for (GLDrawer2D drawer : filterPreviews) {
            drawer.setRect(startX, startY, filterPreviewSize, filterPreviewSize);

            Matrix.setIdentityM(mMvpMatrix, 0);
            Matrix.scaleM(mMvpMatrix, 0,
                    (float) (width / filterPreviewSize),
                    (float) (height / filterPreviewSize),
                    1.0f);

            drawer.setMatrix(mMvpMatrix, 0);

            Log.v(TAG, "updateFiltersUI: scale: " + scale_x + " " + scale_y + " x,y: X:" + startX + " Y:" + startY + " size: " + filterPreviewSize);

            startX = startX + filterPreviewSize + margin;

            if (screenWidth < (startX + filterPreviewSize + margin)) {
                startX = margin;
                startY = startY - filterPreviewSize - margin;   //move down
            }

        }
    }

    private boolean isPortraitMode() {
        int orientation = mContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }

        Log.d(TAG, "isPortraitMode returning false by default");
        return false;
    }
}
