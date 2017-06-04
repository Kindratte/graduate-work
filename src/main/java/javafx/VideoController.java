package javafx;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

import static org.apache.log4j.Logger.getLogger;

public class VideoController {

    @FXML
    private Button button;
    @FXML
    private ImageView currentFrame;
    @FXML
    private ImageView secondFrame;

    private ScheduledExecutorService timer;

    private ServerSocket ssocket;

    private Socket sock;

    private DataInputStream dis;

    private Thread receiver;

    private boolean streamActive;

    private Connection con;

    private BlockingDeque<BufferedImage> images = new LinkedBlockingDeque<>();

    private void startServer() throws IOException {
        ssocket = new ServerSocket(54321);
        streamActive = true;
        receiver = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    con = new Connection(ssocket.accept());
//                    dis = new DataInputStream(sock.getInputStream());
                    receive();
                } catch (IOException e) {
                    System.err.println("Problem on server " + e);
                } finally {
                    Utils.closeQuietly(sock);
                    Utils.closeQuietly(dis);
                }
            }
        });
        receiver.setName("Receiver");
        receiver.start();
    }

    private void receive() {
        try {
                while (!receiver.isInterrupted()) {
                    int len = con.dis.readInt();
                    byte[] buf = new byte[len];

                    con.dis.readFully(buf);

                    System.out.println("length = " + len);
                    ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                    BufferedImage image = ImageIO.read(bais);
                    images.add(image);
                }
        } catch (IOException e) {
            System.err.println("Some problem with receiving frames " + e);
            Thread.currentThread().interrupt();
        } finally {
            Utils.closeQuietly(con.dis);
            Utils.closeQuietly(con.socket);
        }
    }

    @FXML
    void startStreaming() {
        try {
            startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void startCamera() {
//        // set a fixed width for the frame
//        this.currentFrame.setFitWidth(600);
//        // preserve image ratio
//        this.currentFrame.setPreserveRatio(true);

        if (streamActive) {
            if (!images.isEmpty()) {
                Runnable grab = new Runnable() {
                    @Override
                    public void run() {
                        Image imageToShow = null;
                        try {
                            imageToShow = Utils.bufferedImage2Image(images.takeFirst());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        updateImageView(currentFrame, imageToShow);
                    }
                };
                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(grab, 0, 33, TimeUnit.MILLISECONDS);

                this.button.setText("Stop Camera");
                streamActive = false;

            } else {
                streamActive = false;
                System.err.println("Can't take frames from client");
                stopAcquisition();
            }
        } else {
            button.setText("Start Camera");
            stopAcquisition();
        }
    }

    private void stopAcquisition() {
        try {
            timer.shutdown();
            timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            receiver.interrupt();
        } catch (Exception e) {
            System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
        }
    }

    private void updateImageView(ImageView view, Image image) {
        Utils.onFXThread(view.imageProperty(), image);
    }

    void setClosed() {
        stopAcquisition();
    }

    private static class Connection {
        Socket socket;
        DataInputStream dis;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.dis = new DataInputStream(socket.getInputStream());
        }
    }
}