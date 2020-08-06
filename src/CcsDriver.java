//import java.io.UnsupportedEncodingException;
//import java.net.URLEncoder;

public class CcsDriver {
    public static void main(String[] args){

        AutomatedAgent n = new AutomatedAgent();

        System.out.println(n.jsonGetRequest(String.format("http://localhost:5005/conversations/%s/tracker", "Taidgh")));

        n.readProperties();
        n.clientDance();
        n.getCcsServerInfo();
        n.userDance();
    }
}
