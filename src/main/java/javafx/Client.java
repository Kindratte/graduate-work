package javafx;

import org.apache.log4j.Logger;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.log4j.Logger.getLogger;

/**
 * Created by Влад on 23.05.2017.
 */
class Client {

    private static final String HOST = "localhost";

    private static final int PORT = 54321;

    private Socket socket;

    private DataOutputStream sender;

    private OutputStream out;

    private static VideoCapture camera;

    private ScheduledExecutorService timer;

    private boolean cameraActive;

    private static final Logger LOG = getLogger(Client.class);

    private void initialize() {
        camera = new VideoCapture();
        camera.set(Videoio.CV_CAP_PROP_FRAME_WIDTH, 1280);
        camera.set(Videoio.CV_CAP_PROP_FRAME_HEIGHT, 720);
    }

    private void openConnection() {
        try {
            socket = new Socket(HOST, PORT);
            LOG.debug("New socket " + socket.getLocalPort());
            out = socket.getOutputStream();
            sender = new DataOutputStream(out);
        } catch (IOException e) {
            LOG.error("Connection problem " + e);
            e.printStackTrace();
        }
    }

    private void sendFrame(Mat mat) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedImage image = Utils.matToBufferedImage(mat);
            if (image != null) {
                ImageIO.write(image, "jpg", baos);
                byte[] bytes = baos.toByteArray();
                sender.writeInt(bytes.length);
                sender.write(bytes, 0, bytes.length);
                System.out.println("Sent length = " + bytes.length);
                sender.flush();
            } else {
                System.err.println("aaaaaaaaa");
            }
        } catch (IOException e) {
            System.err.println("Some problems with sending" + e);
            this.timer.shutdown();
            try {
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
                camera.release();
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
                camera.release();
            }
        }
    }

    private Mat grabFrame() {

        Mat frame = new Mat();
        if (camera.isOpened()) {
            try {
                camera.read(frame);
            } catch (Exception e) {
                // log the error
                System.err.println("Exception during the frame elaboration: " + e);
            }
        }
        return frame;
    }

    private void startStreaming() {

        if (!this.cameraActive) {
            // start the video camera
            camera.open(0);

            // is the video stream available?
            if (camera.isOpened()) {
                this.cameraActive = true;

                openConnection();

                Runnable grabber = new Runnable() {
                    @Override
                    public void run() {
                        Mat frame = grabFrame();
                        sendFrame(frame);
                        System.out.println("Frame send");
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(grabber, 0, 100, TimeUnit.MILLISECONDS);
            } else {
                // log the error
                System.err.println("Impossible to open the camera connection...");
            }
//        } else {
//            // the camera is not active at this point
//            this.cameraActive = false;
//            // update again the button content
//            VideoController.button.setText("Start Camera");
//
//            // stop the timer
//            VideoController.stopAcquisition();
//        }
        }
    }

    class SenderThread extends Thread {

        private void sendFrame(Mat mat) {
            while (socket.isConnected()) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    BufferedImage image = Utils.matToBufferedImage(mat);
                    if (image != null) {
                        ImageIO.write(image, "jpg", baos);
                        byte[] bytes = baos.toByteArray();
                        sender.writeInt(bytes.length);
                        sender.write(bytes, 0, bytes.length);
                        System.out.println("Sent length = " + bytes.length);
                        sender.flush();
                    }
                } catch (IOException e) {
                    System.out.println("Some problems with sending" + e);
                }
            }
        }

        private Mat grabFrame() {

            Mat frame = new Mat();
            if (camera.isOpened()) {
                try {
                    camera.read(frame);
                } catch (Exception e) {
                    // log the error
                    System.err.println("Exception during the frame elaboration: " + e);
                }
            }
            return frame;
        }

        @Override
        public void run() {
            Mat frame = grabFrame();
            sendFrame(frame);
            System.out.println("Frame send");
        }
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        Client cl = new Client();
        cl.initialize();
        cl.startStreaming();
    }
}

