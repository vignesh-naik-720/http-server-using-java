import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;

public class Main {
  private static String directory;

  public static void main(String[] args) {
    // Parse command line arguments
    for (int i = 0; i < args.length; i++) {
      if ("--directory".equals(args[i]) && i + 1 < args.length) {
        directory = args[i + 1];
      }
    }

    if (directory == null) {
      System.err.println("Directory not specified. Use --directory flag.");
      System.exit(1);
    }

    System.out.println("Logs from your program will appear here!");

    ServerSocket serverSocket = null;

    try {
      serverSocket = new ServerSocket(4221);
      serverSocket.setReuseAddress(true);

      while (true) {
        Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
        new Thread(new ClientHandler(clientSocket, directory)).start();
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
  private String directory;

  public ClientHandler(Socket socket, String directory) {
    this.clientSocket = socket;
    this.directory = directory;
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
      } else if (str.length > 2 && str[1].equals("files")) {
        String filePath = directory + File.separator + str[2];
        File file = new File(filePath);

        if (file.exists() && !file.isDirectory()) {
          byte[] fileContent = Files.readAllBytes(file.toPath());
          String response = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/octet-stream\r\n"
                            + "Content-Length: " + fileContent.length + "\r\n\r\n";
          output.write(response.getBytes());
          output.write(fileContent);
        } else {
          String response = "HTTP/1.1 404 Not Found\r\n\r\n";
          output.write(response.getBytes());
        }
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
