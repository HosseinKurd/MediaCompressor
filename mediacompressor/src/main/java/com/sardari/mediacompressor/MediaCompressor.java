package com.sardari.mediacompressor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.sardari.mediacompressor.videocompression.MediaController;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MediaCompressor {
    private static final String LOG_TAG = MediaCompressor.class.getSimpleName();
    private final static int DEFAULT_VIDEO_WIDTH = 640; //HD
    private final static int DEFAULT_VIDEO_HEIGHT = 480; //HD

    private static Handler handler;
    private static MediaCompressor instance = null;

    public static MediaCompressor with(Context context) {
        if (instance == null) {
            instance = new MediaCompressor();
            handler = new Handler(context.getMainLooper());
        }
        return instance;
    }

    public void compressImage(final String sourcePath, final String destPath, final IMediaCompressor iMediaCompressor) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap scaledBitmap = null;
                BitmapFactory.Options options = new BitmapFactory.Options();

                options.inJustDecodeBounds = true;
                Bitmap bmp = BitmapFactory.decodeFile(sourcePath, options);

                int actualHeight = options.outHeight;
                int actualWidth = options.outWidth;

//      max Height and width values of the compressed image is taken as 816x612
                float maxHeight = 816.0f;
                float maxWidth = 612.0f;
                float imgRatio = actualWidth / actualHeight;
                float maxRatio = maxWidth / maxHeight;

//      width and height values are set maintaining the aspect ratio of the image
                if (actualHeight > maxHeight || actualWidth > maxWidth) {
                    if (imgRatio < maxRatio) {
                        imgRatio = maxHeight / actualHeight;
                        actualWidth = (int) (imgRatio * actualWidth);
                        actualHeight = (int) maxHeight;
                    } else if (imgRatio > maxRatio) {
                        imgRatio = maxWidth / actualWidth;
                        actualHeight = (int) (imgRatio * actualHeight);
                        actualWidth = (int) maxWidth;
                    } else {
                        actualHeight = (int) maxHeight;
                        actualWidth = (int) maxWidth;
                    }
                }

//      setting inSampleSize value allows to load a scaled down version of the original image
                options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

//      inJustDecodeBounds set to false to load the actual bitmap
                options.inJustDecodeBounds = false;

//      this options allow android to claim the bitmap memory if it runs low on memory
                options.inPurgeable = true;
                options.inInputShareable = true;
                options.inTempStorage = new byte[16 * 1024];

                try {
//          load the bitmap from its path
                    bmp = BitmapFactory.decodeFile(sourcePath, options);
                } catch (OutOfMemoryError exception) {
                    exception.printStackTrace();

                }
                try {
                    scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
                } catch (OutOfMemoryError exception) {
                    exception.printStackTrace();
                }

                float ratioX = actualWidth / (float) options.outWidth;
                float ratioY = actualHeight / (float) options.outHeight;
                float middleX = actualWidth / 2.0f;
                float middleY = actualHeight / 2.0f;

                Matrix scaleMatrix = new Matrix();
                scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

                Canvas canvas = new Canvas(scaledBitmap);
                canvas.setMatrix(scaleMatrix);
                canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

                //check the rotation of the image and display it properly
                ExifInterface exif;
                try {
                    exif = new ExifInterface(sourcePath);

                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
                    Log.d("EXIF", "Exif: " + orientation);
                    Matrix matrix = new Matrix();

                    if (orientation == 6) {
                        matrix.postRotate(90);
                        Log.d("EXIF", "Exif: " + orientation);
                    } else if (orientation == 3) {
                        matrix.postRotate(180);
                        Log.d("EXIF", "Exif: " + orientation);
                    } else if (orientation == 8) {
                        matrix.postRotate(270);
                        Log.d("EXIF", "Exif: " + orientation);
                    }
                    scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
                } catch (IOException e) {
                    e.printStackTrace();

                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            iMediaCompressor.failed();
                        }
                    });
                    return;
                }

                try {
                    FileOutputStream out = new FileOutputStream(destPath);
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            iMediaCompressor.failed();
                        }
                    });

                    return;
                }

                runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        iMediaCompressor.success();
                    }
                });
            }
        }).start();
    }

    public void compressVideo(String videoFilePath, String destinationDir, IMediaCompressor iMediaCompressor) {
        compressVideo(videoFilePath, destinationDir, -1, -1, 0, iMediaCompressor);
    }

    public void compressVideo(String videoFilePath, String destinationDir, int scale, Measurement measurement, IMediaCompressor iMediaCompressor) {
        if (measurement.value == Measurement.Width.value) {
            Log.w("TAG", "MediaCompressor_compressVideo_169-> : Measurement.Width");
            compressVideo(videoFilePath, destinationDir, scale, -1, 0, iMediaCompressor);
        } else {
            Log.w("TAG", "MediaCompressor_compressVideo_169-> : Measurement.Height");
            compressVideo(videoFilePath, destinationDir, -1, scale, 0, iMediaCompressor);
        }
    }

    public void compressVideo(final String videoFilePath, final String destinationDir, final int outWidth, final int outHeight, final int bitrate, final IMediaCompressor iMediaCompressor) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                VideoDimen videoDimen = getVideoSize(videoFilePath, outWidth, outHeight);

                Log.w("TAG1", "MediaCompressor_getVideoSize-> : originalWidth= " + videoDimen.originalWidth + " , originalHeight= " + videoDimen.originalHeight);
                Log.w("TAG2", "MediaCompressor_getVideoSize-> : resultWidth  = " + videoDimen.resultWidth + " , resultHeight  = " + videoDimen.resultHeight);

                boolean isConverted = MediaController.getInstance().convertVideo(
                        videoFilePath,
                        destinationDir,
                        videoDimen.resultWidth,
                        videoDimen.resultHeight,
                        bitrate);
                if (isConverted) {
                    Log.v(LOG_TAG, "Video conversion complete");

                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            iMediaCompressor.success();
                        }
                    });
                } else {
                    Log.v(LOG_TAG, "Video conversion in progress");

                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            iMediaCompressor.failed();
                        }
                    });
                }
            }
        }).start();
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    private void runOnUi(Runnable runnable) {
        handler.post(runnable);
    }

    private VideoDimen getVideoSize(String path, int outWidth, int outHeight) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

        int originalWidth = Integer.valueOf(width);
        int originalHeight = Integer.valueOf(height);
        int rotationValue = Integer.valueOf(rotation);

        if (Build.VERSION.SDK_INT < 18 && outHeight > outWidth && outWidth != originalWidth && outHeight != originalHeight) {
            int temp = originalHeight;
            originalHeight = originalWidth;
            originalWidth = temp;
        } else if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                int temp = originalHeight;
                originalHeight = originalWidth;
                originalWidth = temp;
            } else if (rotationValue == 180) {

            } else if (rotationValue == 270) {
                int temp = originalHeight;
                originalHeight = originalWidth;
                originalWidth = temp;
            }
        }

        int resultWidth = -1;
        int resultHeight = -1;

        //calculate scale
        if (outWidth == -1 && outHeight > 0) {
            //change scale from Height
            resultWidth = outHeight * originalWidth / originalHeight;
            resultHeight = outHeight;
        } else if (outWidth > 0 && outHeight == -1) {
            //change scale from Width
            resultWidth = outWidth;
            resultHeight = outWidth * originalHeight / originalWidth;
        } else if (outWidth == -1 && outHeight == -1) {
            //change scale from OriginalWidth & OriginalHeight
            resultWidth = originalWidth;
            resultHeight = originalHeight;
        } else if (outWidth > 0 && outHeight > 0) {
            //change scale from Width & Height
            resultWidth = outWidth;
            resultHeight = outHeight;
        }

        VideoDimen videoDimen = new VideoDimen();
        videoDimen.originalHeight = originalHeight;
        videoDimen.originalWidth = originalWidth;
        videoDimen.resultWidth = resultWidth;
        videoDimen.resultHeight = resultHeight;

        return videoDimen;
    }

    private class VideoDimen {
        private int originalWidth;
        private int originalHeight;
        private int resultWidth;
        private int resultHeight;
    }

    public enum Measurement {
        Width(1),
        Height(2);

        private final int value;

        Measurement(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public interface IMediaCompressor {
        void success();

        void failed();

        void progress(float progress);
    }
}
