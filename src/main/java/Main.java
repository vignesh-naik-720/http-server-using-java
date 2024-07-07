import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");

    // Uncomment this block to pass the first stage
    //
     ServerSocket serverSocket = null;
     Socket clientSocket = null;
    //
     try {
       serverSocket = new ServerSocket(4221);
       // Since the tester restarts your program quite often, setting SO_REUSEADDR
       // ensures that we don't run into 'Address already in use' errors
       serverSocket.setReuseAddress(true);
       clientSocket = serverSocket.accept(); // Wait for connection from client.
       InputStream input = clientSocket.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String line = reader.readLine();
      String[] HttpRequest = line.split(" ", 0);
      if (HttpRequest[1].equals("/")) {
        clientSocket.getOutputStream().write(
            "HTTP/1.1 200 OK\r\n\r\n".getBytes());
      } else if (HttpRequest[1].startsWith("/echo/")) {
        String msg = HttpRequest[1].substring(6);
        String header = String.format(
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
            msg.length(), msg);
        clientSocket.getOutputStream().write(header.getBytes());
      } else {
        clientSocket.getOutputStream().write(
            "HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
      }
     } catch (IOException e) {
       System.out.println("IOException: " + e.getMessage());
     }
  }
}
