//import java.io.UnsupportedEncodingException;
//import java.net.URLEncoder;

public class CcsDriver {
    public static void main(String[] args){
        AutomatedAgent n = new AutomatedAgent();
        String customerName = "Frank";
        String message = "Hell";
        String httpBody =
            	String.format("{\"name\": \"%s\", \"message\": \"%s\"}", customerName, message);
        System.out.println(httpBody);
        n.readProperties();
//        n.createBot();
        n.clientDance();
        n.getCcsServerInfo();
        n.userDance();
        n.answerWebChat();
    }
}
