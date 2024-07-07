import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
  public static void main(String[] args) {
    System.out.println("Logs from your program will appear here!");

    ServerSocket serverSocket = null;

    try {
      serverSocket = new ServerSocket(4221);
      serverSocket.setReuseAddress(true);

      while (true) {
        Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
        new Thread(new ClientHandler(clientSocket)).start();
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}

class ClientHandler implements Runnable {
  private Socket clientSocket;

  public ClientHandler(Socket socket) {
    this.clientSocket = socket;
  }

  @Override
  public void run() {
    try {
      InputStream input = clientSocket.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String line = reader.readLine();
      String[] HttpRequest = line.split(" ");
      OutputStream output = clientSocket.getOutputStream();
      String[] str = HttpRequest[1].split("/");

      if (HttpRequest[1].equals("/")) {
        String response = "HTTP/1.1 200 OK\r\n\r\n";
        output.write(response.getBytes());
      } else if (str.length > 1 && str[1].equals("user-agent")) {
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
          if (line.startsWith("User-Agent:")) {
            String userAgent = line.substring(12).trim();
            String reply = String.format(
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
                userAgent.length(), userAgent);
            output.write(reply.getBytes());
            break;
          }
        }
      } else if (str.length > 2 && str[1].equals("echo")) {
        String responseBody = str[2];
        String finalStr = "HTTP/1.1 200 OK\r\n"
                          + "Content-Type: text/plain\r\n"
                          + "Content-Length: " + responseBody.length() +
                          "\r\n\r\n" + responseBody;
        output.write(finalStr.getBytes());
      } else {
        output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
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
