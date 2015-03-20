import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyServer {
    public static void main(String[] args) throws IOException {
        final ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        System.out.println("Started on: " + args[0]);
        final ExecutorService executorService = Executors.newFixedThreadPool(8);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    serverSocket.close();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                executorService.shutdown();
            }
        });
        while (true) {
            executorService.submit(new ProxyThread(serverSocket.accept()));
        }
    }
}

class ProxyThread implements Runnable {
    private static final int BUFFER_SIZE = 1024 * 4;
    private Socket socket = null;

    public ProxyThread(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        DataOutputStream out = null;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());
            StringBuffer log = new StringBuffer();
            Pattern urlPattern = Pattern.compile("((?:GET)|(?:POST)) (\\S*) (.*)");//GET http://www.baidu.com HTTP/1.1
            Pattern headerPattern = Pattern.compile("([^:]*):(.*)");
            Pattern emptyPattern = Pattern.compile("\\s*");
            String method = null;
            String url = null;
            String protocal;
            Map<String, String> headers = new HashMap<String, String>();
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                log.append(line).append("\n");
                Matcher matcher = urlPattern.matcher(line);
                if (matcher.matches()) {
                    method = matcher.group(1);
                    url = matcher.group(2);
                    protocal = matcher.group(3);
                }
                matcher = headerPattern.matcher(line);
                if (matcher.matches()) {
                    headers.put(matcher.group(1), matcher.group(2));
                }
                matcher = emptyPattern.matcher(line);
                if (matcher.matches()) {
//                    break;
                }
            }
            System.out.println("url:" + url);
            System.out.println("\n\n\n********************" + log.toString() + "\n*********************");
            if (url == null) {
                out.writeBytes("");
            } else {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setDoInput(true);
                conn.setDoOutput(false);
                if(method!=null){
                    conn.setRequestMethod(method);
                }
                for (String k : new String[]{
                        "Host",
                        "Proxy-Connection",
                        "Pragma",
                        "Cache-Control",
                        "Accept",
                        "User-Agent",
                        "Accept-Encoding",
                        "Accept-Language",
                        "Cookie",
                        ""
                }) {
                    String value = headers.get(k);
                    if (value != null) {
                        conn.setRequestProperty(k, value);
                    }
                }

                conn.getResponseMessage();

                out.write(("HTTP/1.1 " + conn.getResponseCode() + " " + conn.getResponseMessage() + "\r\n").getBytes());
                Map<String, List<String>> map = conn.getHeaderFields();
                for (String k : new String[]{"",
                        "Accept-Ranges",
                        "Cache-Control",
                        "Connection",
                        "Content-Encoding",
                        "Content-Length",
                        "Content-Type",
                        "Date",
                        "Expires",
                        "Last-Modified",
                        "Location",
                        "Server",
                        "Set-Cookie",
                        "Vary",
                        "Via",
                        "X-Cache",
                        ""}) {
                    List<String> vs = map.get(k);
                    if (vs != null) {
                        for (String v : vs) {
                            out.write((k + ":" + v).getBytes());
                            out.write("\r\n".getBytes());
                        }
                    }
                }
                out.write("\r\n".getBytes());

                InputStream is = conn.getInputStream();

                byte by[] = new byte[BUFFER_SIZE];
                int index = is.read(by, 0, BUFFER_SIZE);
                while (index != -1) {
                    out.write(by, 0, index);
                    index = is.read(by, 0, BUFFER_SIZE);
                }
                out.flush();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            close(out, in, socket);
        }
    }

    private void close(Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}