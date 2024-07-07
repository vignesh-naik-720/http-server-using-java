import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class Main {
  private static String directory;

  public static void main(String[] args) {
    // Parse command line arguments
    if (args.length > 1 && args[0].equals("--directory")) {
      directory = args[1];
    }
    System.out.println("Logs from your program will appear here!");
    try (ServerSocket serverSocket = new ServerSocket(4221)) {
      serverSocket.setReuseAddress(true);
      while (true) {
        Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
        System.out.println("accepted new connection");
        // Handle each client connection in a separate thread.
        new Thread(() -> handleClient(clientSocket)).start();
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void handleClient(Socket clientSocket) {
    try {
      BufferedReader inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      // Read the request line
      String requestLine = inputStream.readLine();
      String httpMethod = requestLine.split(" ")[0];
      // Read all the headers from the HTTP request.
      Map<String, String> headers = new HashMap<>();
      String headerLine;
      while (!(headerLine = inputStream.readLine()).isEmpty()) {
        String[] headerParts = headerLine.split(": ");
        headers.put(headerParts[0], headerParts[1]);
      }
      // Extract the URL path from the request line.
      String urlPath = requestLine.split(" ")[1];
      OutputStream outputStream = clientSocket.getOutputStream();
      // Write the HTTP response to the output stream.
      String httpResponse = getHttpResponse(httpMethod, urlPath, headers, inputStream);
      System.out.println("Sending response: " + httpResponse);
      outputStream.write(httpResponse.getBytes("UTF-8"));
      // Close the input and output streams.
      inputStream.close();
      outputStream.close();
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    } finally {
      // Close the client socket.
      try {
        if (clientSocket != null) {
          clientSocket.close();
        }
      } catch (IOException e) {
        System.out.println("IOException: " + e.getMessage());
      }
    }
  }

  private static String getHttpResponse(String httpMethod, String urlPath, Map<String, String> headers, BufferedReader inputStream) throws IOException {
    String httpResponse;
    if ("GET".equals(httpMethod)) {
      if ("/".equals(urlPath)) {
        httpResponse = "HTTP/1.1 200 OK\r\n\r\n";
      } else if (urlPath.startsWith("/echo/")) {
        String echoStr = urlPath.substring(6); // Extract the string after "/echo/"
        String contentEncoding = headers.get("Accept-Encoding");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStream responseBodyStream = byteArrayOutputStream;

        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
          responseBodyStream = new GZIPOutputStream(byteArrayOutputStream);
        }
        
        responseBodyStream.write(echoStr.getBytes("UTF-8"));
        responseBodyStream.close(); // Close the stream to complete the gzip compression if used
        byte[] responseBody = byteArrayOutputStream.toByteArray();
        
        StringBuilder responseHeaders = new StringBuilder();
        responseHeaders.append("HTTP/1.1 200 OK\r\n");
        responseHeaders.append("Content-Type: text/plain\r\n");
        responseHeaders.append("Content-Length: ").append(responseBody.length).append("\r\n");
        
        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
          responseHeaders.append("Content-Encoding: gzip\r\n");
        }
        responseHeaders.append("\r\n");
        
        httpResponse = responseHeaders.toString() + new String(responseBody, "UTF-8");
      } else if ("/user-agent".equals(urlPath)) {
        String userAgent = headers.get("User-Agent");
        httpResponse =
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
            userAgent.length() + "\r\n\r\n" + userAgent;
      } else if (urlPath.startsWith("/files/")) {
        String filename = urlPath.substring(7); // Extract the filename after "/files/"
        File file = new File(directory, filename);
        if (file.exists()) {
          byte[] fileContent = Files.readAllBytes(file.toPath());
          httpResponse =
              "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " +
              fileContent.length + "\r\n\r\n" + new String(fileContent);
        } else {
          httpResponse = "HTTP/1.1 404 Not Found\r\n\r\n";
        }
      } else {
        httpResponse = "HTTP/1.1 404 Not Found\r\n\r\n";
      }
    } else if ("POST".equals(httpMethod) && urlPath.startsWith("/files/")) {
      String filename = urlPath.substring(7); // Extract the filename after "/files/"
      File file = new File(directory, filename);
      if (!file.getCanonicalPath().startsWith(new File(directory).getCanonicalPath())) {
        httpResponse = "HTTP/1.1 403 Forbidden\r\n\r\n";
      } else {
        // Get the length of the request body
        int contentLength = Integer.parseInt(headers.get("Content-Length"));
        char[] buffer = new char[contentLength];
        int bytesRead = inputStream.read(buffer, 0, contentLength);
        if (bytesRead == contentLength) {
          // Write the request body to the file
          try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(buffer, 0, bytesRead);
          }
          httpResponse = "HTTP/1.1 201 Created\r\n\r\n";
        } else {
          httpResponse = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
        }
      }
    } else {
      httpResponse = "HTTP/1.1 404 Not Found\r\n\r\n";
    }
    return httpResponse;
  }
}
