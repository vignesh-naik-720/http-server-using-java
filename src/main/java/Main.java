import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import server.RouterBuilder;
import server.Server;
import server.handlers.Echo;
import server.handlers.Index;
import server.handlers.UserAgent;
public class Main {
  public static void main(String[] args) {
    var router = RouterBuilder.create()
                     .exactGET("/user-agent", new UserAgent())
                     .exactGET("/", new Index())
                     .prefixGET("/echo/", new Echo())
                     .build();
    try (var server = new Server(4221, router)) {
      server.run();
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
      e.printStackTrace();
    }
  }
}