import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class ClientHandler implements Runnable {
    private Socket socket;
    ExecutorService pool = SharedPool.pool;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();) {
            int byt;
            RetObj retObj = extractHost(in);

            if (retObj == null ||retObj.host == null){
                String badRequest ="\"HTTP/1.1 400 Bad Request\r\n\r\n\"";
                out.write(badRequest.getBytes(StandardCharsets.UTF_8));
                socket.close();
                out.close();
                in.close();
                return;
            }
            String hostname = retObj.host();

            String targetHost = hostname;
            int port = retObj.isConnect?443 :80;
            if (hostname.contains(":")){
                String[] parts = hostname.split(":");
                targetHost = parts[0];
                port = Integer.parseInt(parts[1]);
            }
            byte[] initialBytes = null;
            if (retObj.isConnect){
                String response = "HTTP/1.1 200 Connection Established\r\n\r\n";
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            else {
                String str = retObj.header().toString();
                str = str.replaceAll("(?i)Connection: keep-alive", "Connection: close");
                initialBytes = str.getBytes(StandardCharsets.UTF_8);
            }

            CountDownLatch countDownLatch = new CountDownLatch(2);

            try(Socket desSocket = new Socket(targetHost,port);
                OutputStream desOut = desSocket.getOutputStream();
                InputStream desIn = desSocket.getInputStream();
                )
            {
                SharedPipe pipeClientToDes = new SharedPipe(in,desOut, countDownLatch,initialBytes);
                SharedPipe pipeDesToClient = new SharedPipe(desIn, out,countDownLatch,initialBytes);
                pool.execute(pipeClientToDes);
                pool.execute(pipeDesToClient);
                countDownLatch.await();

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public record RetObj(String host, StringBuilder header,boolean isConnect) {
    }



    static RetObj extractHost(InputStream in) throws IOException {
        int byt;
        StringBuilder str = new StringBuilder();
        while ((byt = in.read()) != -1) {
            str.append((char) byt);
            if (str.length() >= 4 && str.substring(str.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        String header;
        if (((header=str.toString()).isEmpty())){
            return null;
        }
        System.out.print(str);
        boolean isConnect = header.startsWith("CONNECT");
        String host = null;
        for (String i : str.toString().split("\r\n")) {
            if (i.length() >= 5 && i.substring(0, 5).equalsIgnoreCase("Host:")) {
                host = i.substring(5).trim();
                break;
            }

        }
        if (host == null && isConnect) {
            String[] firstLineParts = header.split(" ");
            if (firstLineParts.length > 1) {
                host = firstLineParts[1];
            }
        }

        return new RetObj(host, str, isConnect);

    }

}
