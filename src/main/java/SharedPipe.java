import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class SharedPipe implements Runnable {
    private InputStream in;
    private OutputStream out;
    private CountDownLatch countDownLatch;
    private  byte[] initialBytes;

    public SharedPipe(InputStream in, OutputStream out, CountDownLatch countDownLatch , byte[] initialBytes) {
        this.in = in;
        this.out = out;
        this.countDownLatch=countDownLatch;
        this.initialBytes=initialBytes;
    }

    @Override
    public void run() {
        try{
            if (initialBytes!=null && initialBytes.length>0){
                out.write(initialBytes);
                out.flush();
            }
            int byt;
            byte[] bytes = new byte[4096];

            while ((byt= in.read(bytes))!=-1){
                out.write(bytes,0,byt);
                out.flush();
            }}
            catch (IOException e){
                e.printStackTrace();
            }
            finally {
                countDownLatch.countDown();

            }
    }
}