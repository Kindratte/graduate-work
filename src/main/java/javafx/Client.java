package javafx;

import org.apache.log4j.Logger;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
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

    private static final Logger log = Logger.getLogger(Client.class);

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
        } catch (ConnectException e) {
                log.error("Connection problem");
                log.debug("Trying to reconnect... ");
                openConnection();
        } catch (IOException e1) {
            log.error("Fatal error! ", e1);
            Utils.closeQuietly(socket);
            Utils.closeQuietly(sender);
            camera.release();
            timer.shutdown();
        }
    }

    private void sendFrame(Mat mat) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedImage image = Utils.matToBufferedImage(mat);
        if (image != null) {
            ImageIO.write(image, "jpg", baos);
            byte[] bytes = baos.toByteArray();
            sender.writeInt(bytes.length);
            sender.write(bytes);
            log.debug("Sent length = " + bytes.length);
            sender.flush();
            log.debug("Frame send");
        } else {
            log.debug("Can't cast mat to image");
        }
    }

    private Mat grabFrame() {

        Mat frame = new Mat();
        if (camera.isOpened()) {
            try {
                camera.read(frame);
            } catch (Exception e) {
                log.error("Exception during the frame elaboration: " + e);
            }
        }
        return frame;
    }

    private void startStreaming() {

        camera.open(0);

        if (camera.isOpened()) {

            openConnection();

            Runnable grabber = () -> {
                Mat frame = grabFrame();
                try {
                    if (socket.isConnected())
                    sendFrame(frame);
                } catch (SocketException e) {
                    Utils.closeQuietly(socket);
                    Utils.closeQuietly(sender);
                    log.debug("Trying to reconnect... ");
                    openConnection();
                } catch (IOException e1) {
                    log.error("Fatal error! ",e1);
                    Utils.closeQuietly(socket);
                    Utils.closeQuietly(sender);
                    camera.release();
                    timer.shutdown();
                }
            };

            timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleAtFixedRate(grabber, 0, 33, TimeUnit.MILLISECONDS);
        } else {
            log.error("Impossible to open the camera connection...");
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

