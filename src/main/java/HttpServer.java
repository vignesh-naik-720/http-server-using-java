import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class HttpServer {
  private final int port;
  private final ExecutorService executorService;
  public HttpServer(final int port, final int concurrencyLevel) {
    this.port = port;
    this.executorService = Executors.newFixedThreadPool(concurrencyLevel);
  }
  public void run() {
    try (ServerSocket serverSocket = new ServerSocket(this.port)) {
      serverSocket.setReuseAddress(true);
      do {
        Socket clientSocket = serverSocket.accept();
        this.executorService.submit(() -> handleRequest(clientSocket));
      } while (true);
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
  private void handleRequest(Socket clientSocket) {
    try {
      // Get and parse URL path input
      InputStream input = clientSocket.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String line = reader.readLine();
      System.out.println(line);
      // Split URL path
      String[] HttpRequest = line.split(" ", 0);
      OutputStream output = clientSocket.getOutputStream();
      if (HttpRequest[1].equals("/")) {
        output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
      } else if (HttpRequest[1].startsWith(
                     "/echo/")) { // return body on /echo/ endpoint
        String msg = HttpRequest[1].substring(6);
        String header = String.format(
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
            msg.length(), msg);
        output.write(header.getBytes());
      } else if (HttpRequest[1].equals("/user-agent")) {
        reader.readLine();
        String userAgent = reader.readLine().split("\\s+")[1];
        String reply = String.format(
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %s\r\n\r\n%s\r\n",
            userAgent.length(), userAgent);
        output.write(reply.getBytes());
      } else {
        output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
      }
      clientSocket.getOutputStream().write(
          "HTTP/1.1 200 OK\r\n\r\n".getBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}