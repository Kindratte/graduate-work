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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class VideoController {

    @FXML
    private Button button1;
    @FXML
    private Button button2;
    @FXML
    private Button button3;
    @FXML
    private Button button4;
    @FXML
    private ImageView firstView;
    @FXML
    private ImageView secondView;
    @FXML
    private ImageView thirdView;
    @FXML
    private ImageView fourthView;

    private ServerSocket ssocket;

    private List<Connection> connectionList = new ArrayList<>(4);

    private static final Logger log = Logger.getLogger(Server.class);

    @FXML
    private void startServer() throws IOException {
        if (ssocket == null) {
            ssocket = new ServerSocket(34343);
            Thread registrar = new Thread(new Registrar());
            registrar.setDaemon(true);
            registrar.start();
        } else {
            log.debug("Server socket already exist!");
        }
    }

    @FXML
    protected void startCamera1() {
        receiveAndStream(connectionList.get(0), firstView, button1);
    }

    @FXML
    private void startCamera2() {
        receiveAndStream(connectionList.get(1), secondView, button2);
    }

    @FXML
    private void startCamera3() {
        receiveAndStream(connectionList.get(2), fourthView, button3);
    }

    @FXML
    private void startCamera4() {
        receiveAndStream(connectionList.get(3), thirdView, button4);
    }

    private void receiveAndStream(Connection con, ImageView frame, Button butt) {
        if (con.streamActive) {

            log.debug("Image queue size is " + con.images.size());

            con.streamActive = false;

            if (!con.isServerStarted) {
                con.startStreaming();
                con.isServerStarted = true;
            }

            Runnable grab = () -> {
                Image imageToShow = null;
                try {
                    imageToShow = Utils.bufferedImage2Image(con.images.takeFirst());
                } catch (Exception e) {
                    log.error("Can't cast buffered image to image \n");
                    e.printStackTrace();
                }
                updateImageView(frame, imageToShow);
            };
            con.timer = Executors.newSingleThreadScheduledExecutor();
            con.timer.scheduleAtFixedRate(grab, 0, 33, TimeUnit.MILLISECONDS);

            butt.setText("Stop Camera");
        } else {
            butt.setText("Start Camera");
            stopAcquisition(con);
            con.streamActive = true;
        }
    }

    private void stopAcquisition(Connection con) {
        try {
            con.timer.shutdown();
            con.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Exception in stopping the frame capture, trying to release the camera now... ", e);
            stopAcquisition(con);
            con.receiver.interrupt();
        }
    }

    private void updateImageView(ImageView view, Image image) {
        Utils.onFXThread(view.imageProperty(), image);
    }

    void setClosed() {
        for (Connection con : connectionList) {
            stopAcquisition(con);
            con.receiver.interrupt();
        }
    }

    private static class Connection {

        private Socket socket;
        private DataInputStream dis;
        private BlockingDeque<BufferedImage> images;
        private boolean streamActive;
        private Thread receiver;
        private ScheduledExecutorService timer;
        private boolean isServerStarted;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.dis = new DataInputStream(socket.getInputStream());
        }

        private void receive() {
            try {
                while (!receiver.isInterrupted()) {
                    int len = dis.readInt();
                    byte[] buf = new byte[len];

                    dis.readFully(buf);

                    if (images.size() < 5) {
                        ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                        BufferedImage image = ImageIO.read(bais);
                        images.add(image);
                    }
                }
            } catch (IOException e) {
                log.error("Some problem with receiving frames ", e);
            } finally {
                Utils.closeQuietly(dis);
                Utils.closeQuietly(socket);
            }
        }

        private void startStreaming() {
            receiver = new Thread(this::receive);
            receiver.start();
        }
    }

    private class Registrar implements Runnable {

        @Override
        public void run() {
            try {
                while (connectionList.size() <= 4) {
                    Connection con = new Connection(ssocket.accept());
                    con.images = new LinkedBlockingDeque<>();
                    connectionList.add(con);
                    log.debug("New connection " + con.socket.getPort());
                    con.streamActive = true;
                    switch (connectionList.size()) {
                        case 1:
                            button1.setVisible(true);
                            break;
                        case 2:
                            button2.setVisible(true);
                            break;
                        case 3:
                            button3.setVisible(true);
                            break;
                        case 4:
                            button4.setVisible(true);
                            break;
                    }
                }
            } catch (IOException e) {
                log.error("Problem on server ", e);
            }
        }
    }
}