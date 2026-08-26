package nl.milton.budgetapp.importers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public final class PdfStatementImporter {
    public interface Callback {
        void onSuccess(BankStatementParser.ParsedStatement statement, String rawText);
        void onError(Exception error);
    }

    private PdfStatementImporter() {}

    public static void importPdf(Context context, Uri uri, Callback callback) {
        try {
            ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor == null) throw new IOException("PDF kon niet worden geopend.");
            PdfRenderer renderer = new PdfRenderer(descriptor);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            StringBuilder text = new StringBuilder();
            readPage(renderer, descriptor, recognizer, 0, text, callback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private static void readPage(PdfRenderer renderer, ParcelFileDescriptor descriptor, TextRecognizer recognizer, int index, StringBuilder text, Callback callback) {
        if (index >= renderer.getPageCount()) {
            try {
                renderer.close();
                descriptor.close();
                recognizer.close();
            } catch (Exception ignored) {}
            String raw = text.toString();
            callback.onSuccess(BankStatementParser.parse(raw), raw);
            return;
        }

        PdfRenderer.Page page = renderer.openPage(index);
        int targetWidth = Math.min(1800, Math.max(1000, page.getWidth() * 2));
        int targetHeight = Math.max(1, Math.round(targetWidth * (page.getHeight() / (float) page.getWidth())));
        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(result -> {
                    text.append(result.getText()).append('\n');
                    bitmap.recycle();
                    readPage(renderer, descriptor, recognizer, index + 1, text, callback);
                })
                .addOnFailureListener(error -> {
                    bitmap.recycle();
                    try {
                        renderer.close();
                        descriptor.close();
                        recognizer.close();
                    } catch (Exception ignored) {}
                    callback.onError(error instanceof Exception ? (Exception) error : new IOException(error));
                });
    }
}
