import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
public class HttpHandler implements Runnable {
  //    public static final String NEW_LINE = "\r\n\r\n";
  public static final String NEW_LINE = "\r\n";
  public static final String SUCCESS = "HTTP/1.1 200 OK" + NEW_LINE;
  public static final String CREATED = "HTTP/1.1 201 Created" + NEW_LINE;
  public static final String NOT_FOUND = "HTTP/1.1 404 Not Found" + NEW_LINE;
  private Socket clientSocket;
  private String baseDir;
  public HttpHandler(Socket clientSocket, String baseDir) {
    this.clientSocket = clientSocket;
    this.baseDir = baseDir;
  }
  @Override
  public void run() {
    try {
      handle();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }
  public void handle() throws IOException, InterruptedException {
    HttpRequest httpRequest = HttpParser.parse(clientSocket);
    if (httpRequest == null)
      return;
    if (httpRequest.getEndpoint().isEmpty())
      sendResponse(SUCCESS);
    else if (httpRequest.getEndpoint().startsWith("echo")) {
      String value = httpRequest.getEndpoint().replaceAll("echo/", "");
      sendResponse(sendSuccessResponse(value, httpRequest));
    } else if (httpRequest.getEndpoint().equals("user-agent")) {
      sendResponse(sendSuccessResponse(
          httpRequest.getHeaders().get("User-Agent"), httpRequest));
    } else if (httpRequest.getEndpoint().startsWith("files")) {
      String fileName = httpRequest.getEndpoint().replaceAll("files/", "");
      if (httpRequest.getHttpMethod().equals("GET")) {
        sendFile(fileName);
        return;
      } else if (httpRequest.getHttpMethod().equals("POST")) {
        saveFile(httpRequest, fileName);
        return;
      }
    } else
      sendResponse(NOT_FOUND);
  }
  private void saveFile(HttpRequest httpRequest, String fileName)
      throws IOException, InterruptedException {
    Path.of(baseDir + "/" + fileName).toFile().createNewFile();
    Files.write(Path.of(baseDir + "/" + fileName),
                httpRequest.getBody().getBytes(), StandardOpenOption.WRITE);
    sendResponse(CREATED);
  }
  public String sendSuccessResponse(String body, HttpRequest httpRequest) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(SUCCESS);
    stringBuilder.append("Content-Type: text/plain" + NEW_LINE);
    if (httpRequest.getHeaders().get("Accept-Encoding") != null &&
        httpRequest.getHeaders().get("Accept-Encoding").contains("gzip"))
      stringBuilder.append("Content-Encoding: gzip" + NEW_LINE);
    stringBuilder.append(String.format(
        "Content-Length: %s%s%s%s", body.length(), NEW_LINE, NEW_LINE, body));
    return stringBuilder.toString();
  }
  public void sendFile(String fileName) throws IOException {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(SUCCESS);
    stringBuilder.append("Content-Type: application/octet-stream" + NEW_LINE);
    byte[] content = null;
    try {
      content = Files.readAllBytes(Path.of(baseDir + "/" + fileName));
    } catch (NoSuchFileException e) {
      sendResponse(NOT_FOUND);
      return;
    }
    stringBuilder.append(
        String.format("Content-Length: %s%s", content.length, NEW_LINE));
    stringBuilder.toString();
    sendResponse(stringBuilder.toString());
    sendResponse(content);
  }
  public void sendResponse(String response) throws IOException {
    response += NEW_LINE;
    sendResponse(response.getBytes());
  }
  public void sendResponse(byte[] message) throws IOException {
    clientSocket.getOutputStream().write(message);
    clientSocket.getOutputStream().flush();
  }
}