package javafx;

import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static org.apache.log4j.Logger.getLogger;

/**
 * Created by Влад on 28.05.2017.
 */
public class Receiver {

    private static final Logger LOG = getLogger(Receiver.class);

    int i = 0;

    void startServer() {
        try (ServerSocket ssocket = new ServerSocket(54321)) {
            System.out.println("Server started on " + ssocket);
            Socket sock = ssocket.accept();
            LOG.debug("Socket accepted" + sock.getPort());
            receive(sock);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void receive(Socket sock) {
        try {
            DataInputStream dis = new DataInputStream(sock.getInputStream());
            while (true) {
                int len = dis.readInt();
                byte[] buf = new byte[len];

                dis.readFully(buf);

                System.out.println("length = " + len);
                ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                BufferedImage image = ImageIO.read(bais);
                File f = new File("c:/new/" + i + ".jpg");
                if (image != null) {
                    ImageIO.write(image, "jpg", f);
                    i++;
                }
                LOG.debug("Frame received" + image);
            }

        } catch (IOException e) {
            System.err.println("Some problem" + e);
        }
    }

    public static void main(String[] args) {
        Receiver rc = new Receiver();
        rc.startServer();
    }
}
