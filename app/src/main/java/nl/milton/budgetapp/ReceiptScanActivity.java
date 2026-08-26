package nl.milton.budgetapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiptScanActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION = 2001;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        previewView = new PreviewView(this);
        root.addView(previewView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(this);
        hint.setText("Bon volledig in beeld • tik daarna op Scan");
        hint.setTextColor(Color.WHITE);
        hint.setBackgroundColor(0x99000000);
        hint.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hintLp.topMargin = dp(24);
        root.addView(hint, hintLp);

        Button capture = new Button(this);
        capture.setText("Scan bon");
        capture.setOnClickListener(v -> captureReceipt(capture));
        FrameLayout.LayoutParams buttonLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        buttonLp.bottomMargin = dp(28);
        root.addView(capture, buttonLp);
        setContentView(root);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                finishWithError("Camera kon niet worden gestart: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureReceipt(Button button) {
        if (imageCapture == null) return;
        button.setEnabled(false);
        button.setText("Bezig…");
        File output = new File(getCacheDir(), "receipt-" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(output).build();
        imageCapture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults result) { runOnUiThread(() -> recognize(output, button)); }
            @Override public void onError(ImageCaptureException exception) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("Scan bon");
                    finishWithError("Foto mislukt: " + exception.getMessage());
                });
            }
        });
    }

    private void recognize(File file, Button button) {
        try {
            Uri uri = Uri.fromFile(file);
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image).addOnSuccessListener(text -> {
                recognizer.close();
                Intent result = new Intent();
                result.putExtra("receipt_text", text.getText());
                setResult(RESULT_OK, result);
                finish();
            }).addOnFailureListener(error -> {
                recognizer.close();
                button.setEnabled(true);
                button.setText("Scan bon");
                finishWithError("Tekstherkenning mislukt: " + error.getMessage());
            });
        } catch (Exception e) {
            button.setEnabled(true);
            button.setText("Scan bon");
            finishWithError("Bon kon niet worden gelezen: " + e.getMessage());
        }
    }

    private void finishWithError(String message) {
        Intent result = new Intent();
        result.putExtra("error", message);
        setResult(RESULT_CANCELED, result);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else if (requestCode == CAMERA_PERMISSION) finishWithError("Camera-toegang is nodig om bonnen te scannen.");
    }

    @Override
    protected void onDestroy() {
        cameraExecutor.shutdown();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
