import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {

     static ExecutorService pool = SharedPool.pool;

    public static void main(String[] args) {

        try (
                ServerSocket serverListener = new ServerSocket(8080)) {
            while(true) {
                Socket clientConnection = serverListener.accept();
                ClientHandler clientHandler = new ClientHandler(clientConnection);
                pool.execute(clientHandler);
            }


        } catch (IOException e) {
            System.out.println("Could not connect to the server. Is it running?");
            e.printStackTrace();
        }
    }
}

