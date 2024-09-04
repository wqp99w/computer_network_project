import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Properties;

public class Main {
    private static Properties props = new Properties(); //데이터베이스 연결을 위한 값 가져오기
    public static void main(String[] args) {
        //config.properties 연결
        try (InputStream input = Main.class.getResourceAsStream("/resources/config.properties")) {
            props.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("JDBC 드라이버를 찾을 수 없습니다.");
            e.printStackTrace();
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(8000)) {
            System.out.println("[Server가 실행되었습니다] Client 연결 대기 중...");

            while (true) {
                try (Socket clientSocket = serverSocket.accept(); //클라이언트가 서버에 연결을 요청하면 서버가 그걸 받아들이고 클라이언트와 통신을 위한 소켓을 반환함
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); //텍스트 데이터 스트림 주고 받기 위한 변수
                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                    System.out.println("Client와 연결되었습니다.");

                    //
                    StringBuilder requestBuilder = new StringBuilder(); //http 요청을 받아오는 객체 생성
                    String line;
                    while (!(line = in.readLine()).isBlank()) { //클라이언트에서 요청이 없을 때끼지 반복
                        requestBuilder.append(line).append("\r\n"); //http 요청은 한번에 여러줄이 들어오기 때문에 빈 칸이 들어올 때까지 요청을 읽어야 한다
                        //"\r\n"은 http 요청의 각 줄을 나타낸다. 따라서 요청 한 줄을 받으면 이를 구분하기 위해서 넣어줘야 한다.
                    }

                    String request = requestBuilder.toString(); //http 요청을 끝까지 다 읽었으면 이를 스트링으로 변환
                    System.out.println("Received request: \n" + request);

                    // Parse the HTTP request line (GET / HTTP/1.1)
                    String[] requestLines = request.split("\r\n"); //requst는 \r\n을 기준으로 분리해서 배열로 만든다.
                    String[] requestLine = requestLines[0].split(" ");
                    //http 요청의 첫번째 줄은 보통 요청 메서드, 요청 경로, http 버전으로 구성된다.
                    // 따라서 공백을 기준으로 문자열을 분리하면 각각 필요한 정보를 얻을 수 있다.
                    String method = requestLine[0];
                    String path = requestLine[1];

                    // Create HTTP response
                    String response;
                    if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) { //메소드가 get이나 head면 일단 get 요청을 수행하는 것은 동일하기 때문
                        response = createResponse(method, path); //http 응답 생성
                    } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                        //메소드가 post, put인 경우 공통적으로 post 메소드를 수행하기 때문에 묶었다.
                        // Read the body of POST/PUT request
                        StringBuilder bodyBuilder = new StringBuilder();
                        while (in.ready()) { //요청 본문이 남아 있는지 확인한다. 헤더는 앞에서 읽었으므로 지금부터 오는 내용은 다 본문이다.
                            bodyBuilder.append((char) in.read());
                        }
                        String body = bodyBuilder.toString();
                        System.out.println(body);
                        response = createResponse(method, path, body); //put, post 메소드는 바디도 존재하기 때문
                    } else {
                        response = "HTTP/1.1 405 Method Not Allowed\r\n\r\n"; //4가지 메소드 이외의 다른 메소드가 들어오는 경우
                    }

                    // 응답 보내기
                    out.print(response);
                    out.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String createResponse(String method, String path) { //get, head 메소드의 응답 생성
        String name = path.substring(path.lastIndexOf("/") + 1); //path가 /get/검색할 이름  <-- 이렇게 들어갔기 때문
        String jsonData = readData(name); //요청한 데이터 데이터베이스에서 검색 후 가져오는 함수
        String response = "";

        //각 에러코드에 대한 에러 메시지 작성 및 헤더 작성
        if(jsonData.equalsIgnoreCase("404")){ //검색하려는 이름이 존재하지 않는 경우
            jsonData = "{\"error\":\"Resource not found\",\"message\":\"The requested resource with " +
                    "name " + name + " was not found in the database.\"}";
            response = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";

        }
        else if(jsonData.equalsIgnoreCase("503")){ //서버에 오류가 있는 경우
            jsonData = "{\"error\":\"Service Unavailable\",\"message\":\"The database is currently " +
                    "unavailable. Please try again later.\"}";
            response = "HTTP/1.1 503 Service Unavailable\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        else{ //정상 동작
            response =  "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        //메시지 길이는 공통으로 구해야 하기 때문에 한번에 처리
        int contentLength = jsonData.length();
        response += contentLength + "\r\n" +
                "\r\n";

        if (!"HEAD".equalsIgnoreCase(method)) { //만약 head 요청이 아니면 헤더에 body까지 추가
            response += jsonData;
        }

        return response;
    }


    private static String readData(String name){
        String[] result = new String[2];
        String jsonData = null;
        try {
            // 데이터베이스 연결
            Connection conn = DriverManager.getConnection(props.getProperty("jdbc.url"),
                    props.getProperty("jdbc.user"), props.getProperty("jdbc.password"));
            System.out.println("데이터베이스 연결 성공");
            // 데이터베이스 작업 수행
            String sql = "SELECT * FROM users WHERE name=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name); // 첫 번째 파라미터에 이름 설정

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {//re에 다음 값이 존재하는 경우
                // 여기서 각 행의 데이터를 가져와서 result에 추가
                result[0] = rs.getString("name");
                result[1] = Integer.toString(rs.getInt("age"));
                jsonData = "{"
                        + "\"name\": \"" + result[0] + "\", "
                        + "\"age\": " + result[1]
                        + "}";
                //데이터베이스 값을 json에 저장
            }

            // 연결 종료
            System.out.println(jsonData);
            conn.close();
            if(jsonData == null) return "404"; //검색한 데이터가 데이터베이스에 존재하지 않을 경우
            return jsonData;
        } catch (SQLException e) {
            System.out.println("데이터베이스 연결 실패");
            e.printStackTrace();
            return "503"; //데이터베이스 서버에 문제가 있는 경우
        }
    }

    private static String createResponse(String method, String path, String body) { //put, post 메소드 응답 생성
        //post, put 메소드는 바디도 같이 들어오니까 빈칸을 기준으로 분리하면 홍진우 23 --> data[0]->홍진우  data[1]-> 23이 된다.
        String[] data = body.split(" ");
        String origin_name = path.substring(path.lastIndexOf("/") + 1); //put 요청의 경우 /put/변경할 이름 <-- 이게 path이기 때문
        //데이터베이스에서 데이터 처리
        String jsonData = insertData(method, origin_name, data[0], data[1].trim());  //data[1]은 \n을 포함하고 있기 때문에 공백 제거해줌
        String response = "";

        if (jsonData.equalsIgnoreCase("400")){ //post,put 메소드에서 나이에 숫자가 아닌 값이 들어왔을 경우
            jsonData = "{\"error\": \"Bad Request\", \"message\": \"Invalid number format for age.\"}";
            response = "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        else if (jsonData.equalsIgnoreCase("404")){ //put 메소드에서 수정할 데이터를 찾지 못하는 경우
            jsonData = "{\"error\":\"Resource not found\",\"message\":\"The requested resource with name " +
                    origin_name + " was not found in the database.\"}";
            response = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        else if (jsonData.equalsIgnoreCase("409")){ //post, put 메소드에서 중복되는 이름을 추가하려는 경우
            jsonData = "{\"error\":\"Conflict\",\"message\":\"The resource with name " + data[0] +
                    " already exists in the database.\"}";
            response = "HTTP/1.1 409 Conflict\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        else if (jsonData.equalsIgnoreCase("500")){ //post 메소드에서 데이터베이스 자체 문제로 추가하지 못하는 경우
            jsonData = "{\"error\":\"Internal Server Error\",\"message\":\"Failed to add data to the database.\"}";
            response = "HTTP/1.1 500 Internal Server Error\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        else if (jsonData.equalsIgnoreCase("503")){ //서버에 문제가 있는 경우
            jsonData = "{\"error\":\"Service Unavailable\",\"message\":\"The database is currently " +
                    "unavailable. Please try again later.\"}";
            response = "HTTP/1.1 503 Service Unavailable\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        else{ //정상 동작, jsonData는 함수에서 반한한 값 그대로 사용
            response =  "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ";
        }
        int contentLength = jsonData.length();

        return response + contentLength + "\r\n" +
                "\r\n" +
                jsonData; //응답 메시지 나머지 작성
    }
    private static String insertData(String method,String origin_name, String name, String age) {
        String jsonData = null;
        try {
            // 데이터베이스 연결
            Connection conn = DriverManager.getConnection(props.getProperty("jdbc.url"),
                    props.getProperty("jdbc.user"), props.getProperty("jdbc.password"));
            System.out.println("데이터베이스 연결 성공");

            if(!origin_name.equalsIgnoreCase(name)) //put 메소드의 경우 원래 있는 데이터의 나이만 바꿀 수도 있기 때문
                if(isNameExists(conn, name)){//추가하려는 데이터가 이미 존재하면 409 에러 발생
                return "409";
                }
            // 데이터베이스 작업 수행
            PreparedStatement pstmt;
            if (method.equalsIgnoreCase("PUT")) { //put 메소드 데이터베이스 처리
                String updateSQL = "UPDATE users SET name = ?, age = ? WHERE name = ?"; //update 문장 준비
                pstmt = conn.prepareStatement(updateSQL); //sql injection 공격 방어하기 유리하기 때문에 사용
                //sql의 파라미터 설정
                pstmt.setString(1, name);
                pstmt.setInt(2, Integer.parseInt(age));
                pstmt.setString(3, origin_name);

                int rowsAffected = pstmt.executeUpdate(); //해당 sql로 인해 영향을 받은 행의 갯수, put은 한 줄만 update를 수행하기 때문에 1줄이다.
                if (rowsAffected > 0) { //1인 경우 업데이트한 내용 json에 저장
                    System.out.println("업데이트 성공");
                    jsonData = "{"
                            + "\"name\": \"" + name + "\", "
                            + "\"age\": " + age
                            + "}";
                    return jsonData;
                } else { //update를 못하는 경우는 해당하는 데이터가 없기 때문
                    System.out.println("업데이트 실패: 대상 레코드가 없음");
                    return "404"; //변경하려는 데이터를 찾을 수 없기 때문에 404에러
                }
            } else if (method.equalsIgnoreCase("POST")) { //post 메소드 데이터베이스 처리
                String sql = "INSERT INTO users (name, age) VALUES (?, ?)";
                pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, name);
                pstmt.setInt(2, Integer.parseInt(age));

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("데이터베이스에 데이터 추가 완료");
                    jsonData = "{"
                            + "\"name\": \"" + name + "\", "
                            + "\"age\": " + age
                            + "}";
                    return jsonData;

                } else {
                    System.out.println("데이터베이스에 데이터 추가 실패");
                    return "500"; //데이터베이스 자체 문제이기 때문
                }
            }
            // 연결 종료
            conn.close();
        } catch (NumberFormatException e){ //age가 String 형식이기 때문에 Int로 바꾸는데, 이때 숫자 형식이 아닐 경우 발생하는 에러 처리
            System.out.println("잘못된 숫자 형식으로 인해 데이터 추가 실패");
            e.printStackTrace();
            return "400"; // 클라이언트에서 잘못된 나이 형식을 입력했기 때문
        } catch (SQLException e) {
            System.out.println("데이터베이스 연결 실패");
            e.printStackTrace();
            return "503";
        }

        return jsonData;
    }

    //post와 put 메소드 수행할 때 추가하려는 값이 데이터베이스에 존재하느지 확인하는 함수
    private static boolean isNameExists(Connection conn, String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) { //sql의 결과가 몇개인지 확인
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count > 0; //0보다 크면 1개 이상이 존재하는 것이므로 데이터를 추가하지 못함
                }
            }
        }
        return false;
    }
}
