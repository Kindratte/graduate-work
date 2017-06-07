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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class VideoController {

    @FXML
    private Button button;
    @FXML
    private ImageView firstFrame;
    @FXML
    private ImageView secondFrame;

    private ScheduledExecutorService timer;

    private ServerSocket ssocket;

    private Socket sock;

    private DataInputStream dis;

    private Thread receiver;

    private boolean streamActive;

    private List<Connection> connectionList = new ArrayList<>(4);

    @FXML
    private void startServer() throws IOException {
            ssocket = new ServerSocket(45000);
            streamActive = true;
            Thread registrar = new Thread(new Registrar());
            registrar.setName("Registrar");
            registrar.start();
    }

    private void receive(Connection con) {
        try {
            while (!receiver.isInterrupted()) {
                int len = con.dis.readInt();
                byte[] buf = new byte[len];

                con.dis.readFully(buf);

                System.out.println("length = " + len);
                ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                BufferedImage image = ImageIO.read(bais);
                con.images.add(image);
            }
        } catch (IOException e) {
            System.err.println("Some problem with receiving frames " + e);
            Thread.currentThread().interrupt();
        } finally {
            Utils.closeQuietly(con.dis);
            Utils.closeQuietly(con.socket);
        }
    }

    private void startStreaming(Connection con) {
        receiver = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!connectionList.isEmpty()) {
                    receive(con);
                }
            }
        });
        receiver.setName("Receiver");
        receiver.start();
    }

    @FXML
    protected void startCamera1() {
        receiveAndStream(connectionList.get(0),firstFrame);
    }

    @FXML
    private void startCamera2() {
        receiveAndStream(connectionList.get(1),secondFrame);
    }

    private void receiveAndStream(Connection con, ImageView frame) {
        if(streamActive) {
            if (con != null) {
                startStreaming(con);
                Runnable grab = new Runnable() {
                    @Override
                    public void run() {
                        Image imageToShow = null;
                        try {
                            imageToShow = Utils.bufferedImage2Image(con.images.takeFirst());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        updateImageView(frame, imageToShow);
                    }
                };
                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(grab, 0, 33, TimeUnit.MILLISECONDS);

                this.button.setText("Stop Camera");
                streamActive = false;

            } else {
                streamActive = false;
                System.err.println("Can't take frames from client");
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
//            receiver.interrupt();
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
        BlockingDeque<BufferedImage> images;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.dis = new DataInputStream(socket.getInputStream());
        }
    }

    private class Registrar implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Connection con = new Connection(ssocket.accept());
                    con.images = new LinkedBlockingDeque<>();
                    connectionList.add(con);
                    System.out.println("New connection " + con.socket.getPort());
                    streamActive = true;
                }
            } catch (IOException e) {
                System.err.println("Problem on server " + e);
            } finally {
                Utils.closeQuietly(sock);
                Utils.closeQuietly(dis);
            }
        }
    }
}