package com.example.corndisease;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_GALLERY = 2;
    private static final int REQUEST_PERMISSION = 10;

    private Button btnCamera, btnGallery, btnDetect, btnCloseImage;
    private ImageView ivPreview;
    private TextView tvResult;
    private Bitmap selectedBitmap;
    private ObjectDetector detector;

    // 病害标签（按你的模型训练顺序修改！）
    private final List<String> labels = new ArrayList<String>(){{
        add("玉米大斑病");
        add("玉米小斑病");
        add("玉米锈病");
        add("健康叶片");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initDetector();
        checkPermissions();
    }

    private void initViews() {
        btnCamera = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        btnDetect = findViewById(R.id.btn_detect);
        btnCloseImage = findViewById(R.id.btn_close_image);
        ivPreview = findViewById(R.id.iv_preview);
        tvResult = findViewById(R.id.tv_result);

        btnCamera.setOnClickListener(v -> openCamera());
        btnGallery.setOnClickListener(v -> openGallery());
        btnCloseImage.setOnClickListener(v -> clearImage());
        btnDetect.setOnClickListener(v -> runDetection());
    }

    private void initDetector() {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(5)
                            .setScoreThreshold(0.5f)
                            .build();
            detector = ObjectDetector.createFromFileAndOptions(
                    this,
                    "rt_detr_corn_disease.tflite",
                    options
            );
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "模型加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_PERMISSION);
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_CAMERA);
        }
    }

    private void openGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhotoIntent, REQUEST_GALLERY);
    }

    private void clearImage() {
        selectedBitmap = null;
        ivPreview.setImageBitmap(null);
        ivPreview.setVisibility(ImageView.GONE);
        btnCloseImage.setVisibility(Button.GONE);
        tvResult.setText("检测结果将显示在这里");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CAMERA) {
                Bundle extras = data.getExtras();
                selectedBitmap = (Bitmap) extras.get("data");
                showImage(selectedBitmap);
            } else if (requestCode == REQUEST_GALLERY) {
                Uri selectedImageUri = data.getData();
                try {
                    InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                    selectedBitmap = BitmapFactory.decodeStream(inputStream);
                    showImage(selectedBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showImage(Bitmap bitmap) {
        ivPreview.setImageBitmap(bitmap);
        ivPreview.setVisibility(ImageView.VISIBLE);
        btnCloseImage.setVisibility(Button.VISIBLE);
        tvResult.setText("检测结果将显示在这里");
    }

    private void runDetection() {
        if (selectedBitmap == null) {
            Toast.makeText(this, "请先选择或拍摄一张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (detector == null) {
            Toast.makeText(this, "模型未加载成功", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
                .build();
        TensorImage tensorImage = TensorImage.fromBitmap(selectedBitmap);
        tensorImage = imageProcessor.process(tensorImage);

        List<Detection> results = detector.detect(tensorImage);

        Bitmap resultBitmap = drawDetectionResult(selectedBitmap, results);
        ivPreview.setImageBitmap(resultBitmap);

        StringBuilder resultText = new StringBuilder("检测结果:\n");
        for (Detection detection : results) {
            String label = labels.get((int) detection.getCategories().get(0).getIndex());
            float score = detection.getCategories().get(0).getScore();
            resultText.append(String.format("%s: %.2f%%\n", label, score * 100));
        }
        if (results.isEmpty()) {
            resultText.append("未检测到病害");
        }
        tvResult.setText(resultText.toString());
    }

    private Bitmap drawDetectionResult(Bitmap bitmap, List<Detection> results) {
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);

        Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(true);

        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.RED);
        bgPaint.setStyle(Paint.Style.FILL);

        for (Detection detection : results) {
            RectF boundingBox = detection.getBoundingBox();
            String label = labels.get((int) detection.getCategories().get(0).getIndex());
            float score = detection.getCategories().get(0).getScore();

            canvas.drawRect(boundingBox, boxPaint);

            String text = label + " " + String.format("%.1f%%", score * 100);
            float textWidth = textPaint.measureText(text);
            float textHeight = textPaint.getTextSize();
            canvas.drawRect(
                    boundingBox.left,
                    boundingBox.top - textHeight - 4,
                    boundingBox.left + textWidth + 8,
                    boundingBox.top,
                    bgPaint
            );

            canvas.drawText(text, boundingBox.left + 4, boundingBox.top - 4, textPaint);
        }

        return mutableBitmap;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "请授予" + permissions[i] + "权限", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) {
            detector.close();
        }
    }
}