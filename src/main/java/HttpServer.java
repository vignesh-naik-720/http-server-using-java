import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

public class HttpServer {
  private final int port;
  private final ExecutorService executorService;
  private final String directory;

  public HttpServer(final int port, final int concurrencyLevel, final String directory) {
    this.port = port;
    this.executorService = Executors.newFixedThreadPool(concurrencyLevel);
    this.directory = directory;
  }

  public static void main(String[] args) {
    if (args.length != 2 || !"--directory".equals(args[0])) {
      System.err.println("Usage: java HttpServer --directory <path>");
      System.exit(1);
    }

    String directory = args[1];

    HttpServer server = new HttpServer(4221, 10, directory);  // Default port and concurrency level
    server.run();
  }

  public void run() {
    try (ServerSocket serverSocket = new ServerSocket(this.port)) {
      serverSocket.setReuseAddress(true);
      System.out.println("Server started on port " + port);

      while (true) {
        Socket clientSocket = serverSocket.accept();
        this.executorService.submit(() -> handleRequest(clientSocket));
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    } finally {
      this.executorService.shutdown();
    }
  }

  private void handleRequest(Socket clientSocket) {
    try {
      InputStream input = clientSocket.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String requestLine = reader.readLine();
      System.out.println(requestLine);

      if (requestLine == null) {
        return;
      }

      String[] HttpRequest = requestLine.split("\\s+", 0);
      if (HttpRequest.length < 2) {
        return;
      }

      // Read all the headers from the HTTP request.
      Map<String, String> headers = new HashMap<>();
      String headerLine;
      while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
        String[] headerParts = headerLine.split(":", 2);
        if (headerParts.length > 1) {
          headers.put(headerParts[0].trim().toLowerCase(), headerParts[1].trim());
        }
      }

      OutputStream output = clientSocket.getOutputStream();
      String response;
      if (HttpRequest[0].equals("GET")) {
        if (HttpRequest[1].equals("/")) {
          response = "HTTP/1.1 200 OK\r\n\r\n";
        } else if (HttpRequest[1].startsWith("/echo/")) {
          String msg = HttpRequest[1].substring(6);
          String contentEncodings = headers.getOrDefault("accept-encoding", "");
          if (contentEncodings.contains("gzip")) {
            response = "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Type: text/plain\r\nContent-Length: " +
                       msg.length() + "\r\n\r\n";
            output.write(response.getBytes());
            try (GZIPOutputStream gzipOutput = new GZIPOutputStream(output)) {
              gzipOutput.write(msg.getBytes());
            }
          } else {
            response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                       msg.length() + "\r\n\r\n" + msg;
            output.write(response.getBytes());
          }
        } else if (HttpRequest[1].equals("/user-agent")) {
          String userAgent = headers.get("user-agent");
          if (userAgent == null) {
            userAgent = "";
          }
          response = String.format(
              "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
              userAgent.length(), userAgent);
          output.write(response.getBytes());
        } else if (HttpRequest[1].startsWith("/files/")) {
          String fileName = HttpRequest[1].substring(7);
          File file = new File(directory, fileName);
          if (file.exists() && !file.isDirectory()) {
            byte[] fileContents = Files.readAllBytes(file.toPath());
            response = String.format(
                "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: %d\r\n\r\n",
                fileContents.length);
            output.write(response.getBytes());
            output.write(fileContents);
          } else {
            response = "HTTP/1.1 404 Not Found\r\n\r\n";
            output.write(response.getBytes());
          }
        } else {
          response = "HTTP/1.1 404 Not Found\r\n\r\n";
          output.write(response.getBytes());
        }
      } else if (HttpRequest[0].equals("POST")) {
        if (HttpRequest[1].startsWith("/files/")) {
          StringBuilder bodyBuffer = new StringBuilder();
          while (reader.ready()) {
            bodyBuffer.append((char) reader.read());
          }
          String body = bodyBuffer.toString();
          File file = new File(directory + HttpRequest[1].substring(7));
          if (file.createNewFile()) {
            try (FileWriter writer = new FileWriter(file)) {
              writer.write(body);
            }
            response = "HTTP/1.1 201 Created\r\n\r\n";
          } else {
            response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
          }
          output.write(response.getBytes());
        }
      } else {
        response = "HTTP/1.1 404 Not Found\r\n\r\n";
        output.write(response.getBytes());
      }
      output.flush();
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    } finally {
      try {
        clientSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
