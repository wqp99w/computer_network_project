import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 8000); //소켓 생성
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true); //텍스트데이터 스트림 주고받기 위한 변수
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to server");

            //어떤 메소드 요청할 것인지 입력
            System.out.print("요청할 HTTP method를 골라주세요. (GET, HEAD, POST, PUT): ");
            String method = scanner.nextLine().toUpperCase();

            // http request 작성
            String path = "/";
            String request = "";
            if ("GET".equals(method) || "HEAD".equals(method)) { //get, head 메소드는 데이터베이스에서 이름으로 데이터를 검색
                System.out.print("검색할 이름을 입력해 주세요: ");
                String name = scanner.nextLine();
                switch (method) { //메소드에 따라 path 작성
                    case "GET":
                        path += "get/";
                        break;
                    case "HEAD":
                        path += "head/";
                        break;
                }
                path += name; //검색할 이름 path에 삽입
                request += method + " " + path + " HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "Connection: close\r\n"; //request 헤더 작성 완료
            }

            if ("POST".equals(method) || "PUT".equals(method)) {
                switch (method) {
                    case "POST":
                        path += "post/";
                        break;
                    case "PUT": //put 메소드는 바꿀 이름일 path에 추가한다
                        path += "put/";
                        System.out.print("바꿀 이름을 입력해주세요: ");
                        String name = scanner.nextLine();
                        path += name;
                        break;
                }
                //추가할 데이터 입력
                System.out.print("이름을 입력해주세요: ");
                String name = scanner.nextLine();
                System.out.print("나이를 입력해주세요: ");
                String age = scanner.nextLine();

                //put, post 요청 메시지 작성
                request += method + " " + path + " HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "Connection: close\r\n";
                request += "Content-Length: " + (name.length() + age.length()) + "\r\n" +
                        "Content-Type: application/json\r\n\r\n" +
                        name + ' ' + age + "\r\n";
            } else {
                request += "\r\n";
            }

            // Send HTTP request
            out.print(request);
            out.flush();

            // Read HTTP response
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            //응답 메시지가 없을 때 까지 반복해서 받기
            while ((line = in.readLine()) != null) {
                responseBuilder.append(line).append("\r\n");
            }

            //응답 메시지 출력
            System.out.println("Received response: \n" + responseBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
