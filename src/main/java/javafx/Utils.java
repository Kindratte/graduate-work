package javafx;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.Closeable;
import java.io.IOException;


public final class Utils {

    public static Image bufferedImage2Image(BufferedImage bi) {
        try {
            return SwingFXUtils.toFXImage(bi, null);
        } catch (Exception e) {
            System.err.println("Cannot convert the BufferedImage" + e);
            return null;
        }
    }

    public static <T> void onFXThread(final ObjectProperty<T> property, final T value) {
        Platform.runLater(() -> {
            property.set(value);
        });
    }

    public static BufferedImage matToBufferedImage(Mat original) {

        BufferedImage image;
        int width = original.width(), height = original.height(), channels = original.channels();
        byte[] sourcePixels = new byte[width * height * channels];
        original.get(0, 0, sourcePixels);

        image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);

        return image;
    }

    public static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                System.out.println("Problem with closing " + c);
            }
        }
    }
}
