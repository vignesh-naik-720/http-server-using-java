import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class HttpServer {
  private final int port;
  private final ExecutorService executorService;
  private final String directory;
  public HttpServer(final int port, final int concurrencyLevel,
                    final String directory) {
    this.port = port;
    this.executorService = Executors.newFixedThreadPool(concurrencyLevel);
    this.directory = directory;
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
      String requestLine = reader.readLine();
      System.out.println(requestLine);
      // Split URL path
      String[] HttpRequest = requestLine.split("\\s+", 0);
      // Read all the headers from the HTTP request.
      Map<String, String> headers = new HashMap<>();
      String headerLine;
      while ((headerLine = reader.readLine()) != null &&
             !headerLine.isEmpty()) {
        String[] headerParts = headerLine.split(":");
        if (headerParts.length > 1) {
          headers.put(headerParts[0].trim().toLowerCase(),
                      headerParts[1].trim());
        }
      }
      OutputStream output = clientSocket.getOutputStream();
      String response = "HTTP/1.1 404 Not Found\r\n\r\n";
      if (HttpRequest[0].equals("GET")) {
        if (HttpRequest[1].equals("/")) {
          output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
        } else if (HttpRequest[1].startsWith(
                       "/echo/")) { // return body on /echo/ endpoint
          String msg = HttpRequest[1].substring(6);
          String contentEncodings = headers.getOrDefault("accept-encoding", "");
          if (contentEncodings.contains("gzip")) {
            response =
                "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Type: text/plain\r\nContent-Length: " +
                msg.length() + "\r\n\r\n" + msg;
          } else {
            response =
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                msg.length() + "\r\n\r\n" + msg;
          }
        } else if (HttpRequest[1].equals("/user-agent")) {
          String userAgent = headers.get("user-agent");
          response = String.format(
              "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %s\r\n\r\n%s\r\n",
              userAgent.length(), userAgent);
        } else if (HttpRequest[1].startsWith("/files/")) {
          String fileName = HttpRequest[1].substring(7);
          File file = new File(directory, fileName);
          if (file.exists()) {
            byte[] fileContents = Files.readAllBytes(file.toPath());
            response = String.format(
                "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: %s\r\n\r\n%s\r\n",
                fileContents.length, new String(fileContents));
          } else {
            response = "HTTP/1.1 404 Not Found\r\n\r\n";
          }
        }
      } else if (HttpRequest[0].equals("POST")) {
        if (HttpRequest[1].startsWith("/files/")) {
          StringBuilder bodyBuffer = new StringBuilder();
          while (reader.ready()) {
            bodyBuffer.append((char)reader.read());
          }
          String body = bodyBuffer.toString();
          File file = new File(directory + HttpRequest[1].substring(7));
          if (file.createNewFile()) {
            FileWriter writer = new FileWriter(file);
            writer.write(body);
            writer.close();
          }
          response = "HTTP/1.1 201 Created\r\n\r\n";
        }
      }
      output.write(response.getBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}