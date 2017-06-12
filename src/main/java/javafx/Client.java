package javafx;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Влад on 23.05.2017.
 */
class Client {

    private SocketAddress addr;

    private DataOutputStream sender;

    private Socket socket;

    private static VideoCapture camera;

    private ScheduledExecutorService timer;

    private ByteArrayOutputStream baos;

    public Client(SocketAddress addr) {
        this.addr = addr;
        camera = new VideoCapture();
        camera.set(Videoio.CV_CAP_PROP_FRAME_WIDTH, 1280);
        camera.set(Videoio.CV_CAP_PROP_FRAME_HEIGHT, 720);
    }

    private void openConnection() {
        try {
            socket = new Socket();
            socket.connect(addr);
            sender = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            System.err.println("Connection problem " + e);
            e.printStackTrace();
        }
    }

    private void sendFrame(Mat mat) {
        try {
            baos = new ByteArrayOutputStream();
            BufferedImage image = Utils.matToBufferedImage(mat);
            if (image != null) {
                ImageIO.write(image, "jpg", baos);
                byte[] bytes = baos.toByteArray();
                sender.writeInt(bytes.length);
                sender.write(bytes, 0, bytes.length);
                System.out.println("Sent length = " + bytes.length);
                sender.flush();
            } else {
                System.err.println("Can't cast mat to image");
            }
        } catch (IOException e) {
            System.err.println("Some problems with sending " + e);
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
                System.err.println("Exception during the frame elaboration: " + e);
            }
        }
        return frame;
    }

    private void startStreaming() {

        camera.open(0);

        if (camera.isOpened()) {

            openConnection();

            Runnable grabber = () -> {
                {
                    Mat frame = grabFrame();
                    sendFrame(frame);
                    System.out.println("Frame send");
                }
            };

            this.timer = Executors.newSingleThreadScheduledExecutor();
            this.timer.scheduleAtFixedRate(grabber, 0, 33, TimeUnit.MILLISECONDS);
        } else {
            System.err.println("Impossible to open the camera connection...");
        }
    }

    private static SocketAddress parseAddress(String addr) {
        String[] split = addr.split(":");
        return new InetSocketAddress(split[0], Integer.parseInt(split[1]));
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        String addr = null;

        if (args != null && args.length > 0)
            addr = args[0];

        Scanner scanner = new Scanner(System.in);

        if (addr == null) {
            System.out.println("Enter server address");
            addr = scanner.nextLine();
        }

        Client cl = new Client(parseAddress(addr));
        cl.startStreaming();
    }
}

