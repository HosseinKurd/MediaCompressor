package com.sardari.sample;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sardari.mediacompressor.MediaCompressor;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_TYPE_VIDEO = 1;
    private static final int REQUEST_TYPE_IMAGE = 2;

    private String sourceFilePath, destFilePath;
    private Uri compressUri = null;
    private ImageView img_Photo;
    private TextView picDescription;
    private Button btn_SelectVideo, btn_SelectPhoto;
    private LinearLayout compressionMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_SelectVideo = findViewById(R.id.btn_SelectVideo);
        btn_SelectPhoto = findViewById(R.id.btn_SelectPhoto);
        img_Photo = findViewById(R.id.img_Photo);
        picDescription = findViewById(R.id.pic_description);
        compressionMsg = findViewById(R.id.compressionMsg);

        btn_SelectVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachVideo();
            }
        });

        btn_SelectPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachPhoto();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            String[] projection = {MediaStore.MediaColumns.DATA};
            Cursor cursor = getContentResolver().query(selectedImageUri, projection, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
            cursor.moveToFirst();
            sourceFilePath = cursor.getString(column_index);
            cursor.close();

            destFilePath = getTempFile(sourceFilePath.substring(sourceFilePath.lastIndexOf(".")));

            Log.w("path1", "SourceFilePath= " + sourceFilePath);
            Log.w("path2", "DestFilePath  = " + destFilePath);

            if (data.getData() != null) {
                if (requestCode == REQUEST_TYPE_VIDEO) {
                    compressionMsg.setVisibility(View.VISIBLE);
                    picDescription.setVisibility(View.GONE);

                    MediaCompressor.with(this).compressVideo(sourceFilePath, destFilePath, 640, MediaCompressor.Measurement.Width, new MediaCompressor.MediaCompressorListener() {
                        @Override
                        public void success() {
                            File file = new File(destFilePath);
                            float length = file.length() / 1024f; // Size in KB
                            String value;

                            if (length >= 1024)
                                value = length / 1024f + " MB";
                            else
                                value = length + " KB";

                            String text = String.format(Locale.US, "%s\nName: %s\nSize: %s", getString(R.string.video_compression_complete), file.getName(), value);

//                            File file = new File(destFilePath);
//                            String text = String.format(Locale.US, "%s\nName: %s\nSize: %s", getString(R.string.video_compression_complete), file.getName(), file.length());

                            compressionMsg.setVisibility(View.GONE);
                            picDescription.setVisibility(View.VISIBLE);
                            picDescription.setText(text);

                            addVideoToGallery(file);
                        }

                        @Override
                        public void failed() {

                        }

                        @Override
                        public void progress(float progress) {
                            Log.w("TAG", "MainActivity_progress_147-> :" + progress);
                        }
                    });
                } else if (requestCode == REQUEST_TYPE_IMAGE) {
                    MediaCompressor.with(this).compressImage(sourceFilePath, destFilePath, new MediaCompressor.MediaCompressorListener() {
                        @Override
                        public void success() {
                            File imageFile = new File(destFilePath);
                            compressUri = Uri.fromFile(imageFile);

                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), compressUri);
                                img_Photo.setImageBitmap(bitmap);

                                String name = imageFile.getName();
                                float length = imageFile.length() / 1024f; // Size in KB
                                int compressWidth = bitmap.getWidth();
                                int compressHieght = bitmap.getHeight();
                                String text = String.format(Locale.US, "Name: %s\nSize: %fKB\nWidth: %d\nHeight: %d", name, length, compressWidth, compressHieght);
                                picDescription.setVisibility(View.VISIBLE);
                                picDescription.setText(text);

                                addPhotoToGallery(imageFile);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void failed() {

                        }

                        @Override
                        public void progress(float progress) {
                        }
                    });
                }
            }
        }
    }

    public void attachVideo() {
        Intent library = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        library.setType("video/*");
        startActivityForResult(Intent.createChooser(library, "Video"), REQUEST_TYPE_VIDEO);
    }

    public void attachPhoto() {
        Intent library = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        library.setType("image/*");
        startActivityForResult(Intent.createChooser(library, "Photo"), REQUEST_TYPE_IMAGE);
    }

    public String getTempFile(String ext) {
        File file = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "MediaCompressor");

        if (!file.exists()) {
            file.mkdirs();
        }

        String fileName = String.valueOf(System.currentTimeMillis()) + ext;
        return file.getAbsolutePath() + File.separator + fileName;
    }

    public void addVideoToGallery(File videoFile) {
        ContentValues values = new ContentValues(3);
        values.put(MediaStore.Video.Media.TITLE, "My video title");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATA, videoFile.getAbsolutePath());
        getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }

    public void addPhotoToGallery(File videoFile) {
        ContentValues values = new ContentValues(3);
        values.put(MediaStore.Video.Media.TITLE, "My Photo title");
        values.put(MediaStore.Video.Media.DATA, videoFile.getAbsolutePath());
        getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }
}
