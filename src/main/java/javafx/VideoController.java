package javafx;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class VideoController {

    @FXML
    private Button button;
    @FXML
    private ImageView currentFrame;
    @FXML
    private Button butt;

    // a timer for acquiring the video stream
    private ScheduledExecutorService timer;

    private ServerSocket ssocket;

    private Socket sock;

    private DataInputStream dis;

    private Runnable receiver;

    private BlockingDeque<BufferedImage> images = new LinkedBlockingDeque<>();

    void startServer() throws IOException {
        ssocket = new ServerSocket(54321);
        receiver = new Runnable() {
            @Override
            public void run() {
                try {
                    sock = ssocket.accept();
                    if(sock.isConnected())
                    receive(sock);
                } catch (IOException e) {
                    System.err.println("Problem on server " + e);
                } finally {
                    try {
                        ssocket.close();
                    } catch (IOException e) {
                        //Ignore!
                    }
                }
            }
        };
        new Thread(receiver).start();
    }

    private void receive(Socket sock) {
        try {
            dis = new DataInputStream(sock.getInputStream());
            while (sock.isConnected()) {
                int len = dis.readInt();
                byte[] buf = new byte[len];

                dis.readFully(buf);

                System.out.println("length = " + len);
                ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                BufferedImage image = ImageIO.read(bais);
                images.add(image);
            }

        } catch (IOException e) {
            System.err.println("Some problem with receiving frames " + e);
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

    /**
     * The action triggered by pushing the button on the GUI
     */

    @FXML
    protected void startCamera() {
        // set a fixed width for the frame
        this.currentFrame.setFitWidth(600);
        // preserve image ratio
        this.currentFrame.setPreserveRatio(true);

        startStreaming();
        if(sock.isConnected()) {
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

                // update the button content
                this.button.setText("Stop Camera");
            } else {
                System.err.println("Can't take frames from client");
            }
        } else {
            this.button.setText("Start Camera");
            this.stopAcquisition();
        }
    }

    /**
     * Stop the acquisition from the camera and release all the resources
     */
    private void stopAcquisition() {
        if (this.timer != null && !this.timer.isShutdown()) {
            try {
                // stop the timer
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // log any exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }
        }
    }

    /**
     * Update the {@link ImageView} in the JavaFX main thread
     *
     * @param view  the {@link ImageView} to update
     * @param image the {@link Image} to show
     */
    private void updateImageView(ImageView view, Image image) {
        Utils.onFXThread(view.imageProperty(), image);
    }

    /**
     * On application close, stop the acquisition from the camera
     */
    void setClosed() {
        this.stopAcquisition();

    }

}