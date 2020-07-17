// Import our bot of choice for testing, in this case its alicebot
import com.avaya.ccs.api.*;
import com.avaya.ccs.api.enums.Profile;
import com.avaya.ccs.api.exceptions.InvalidArgumentException;
import com.avaya.ccs.api.exceptions.SecurityException;
import com.avaya.ccs.core.Client;
import com.avaya.ccs.core.SecurityContext;
import com.avaya.ccs.core.Session;
import org.alicebot.ab.Bot;
import org.alicebot.ab.Chat;
import org.alicebot.ab.MagicStrings;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

// Import the Avaya CCS API


// BotInterface.java
//
public class AvayaAliceBot implements BotInterface {
    // This is for use with Program AB and AIML
    Bot bot;
    Chat chatSession;

    // This is part of the CCS API
    Client avayaClientInstance;
    // Security context for secure connection to server
    SecurityContext avayaSecurityContext;
    // Setting the profile as an agent desktop, as currently only one agent exists
    Profile avayaProfile = Profile.AgentDesktop;

    Session CCSSession;

    KeyStore ks;
    SecurityContext secCon;

    char[] password = new char[] {'*', '*', '*', '*'};

    // Give the bot a name
    static String botName = "Avaya";


    // This sends information from the client to the server
    ClientListenerI avayaClientListener = clientEvent -> {

        // This notification event should contain a notificationType object, a ClientI object
        // An optional ErrorI object and an optional ResponseData object

        System.out.println("To be instanced correctly...");

    };

    // This sends information from the session to the server
    SessionListenerI avayaSessionListener = sessionEvent -> {

        // This notification event should contain a notificationType object, a SessionI object
        // An optional ErrorI object and an optional ResponseData object

        System.out.println("To be instanced correctly...");
    };

    // Create a new bot instance
    public Bot createBot(String botName) {
        // Get the location of the bot details - the AIML files
        String resourcesPath = getResourcesPath();
        System.out.println("Resource path: " + resourcesPath);

        // Create the bot from the Alice Bot code base
        Bot bot = new Bot(botName, resourcesPath);

        // Create a new instance of an Avaya CCS Client

        /* Connectivity to CCS API commented out, not functioning outside Avaya intranet
        try {
            // First we need to create a KeyStore
            try {
                ks = KeyStore.getInstance("JKS");
                InputStream keyStoreStream = new FileInputStream(getResourcesPath() + "/keyStore.jks");
                ks.load(keyStoreStream, password);
            } catch (Exception e) {
                e.printStackTrace();
            }


            // Then we need to create a security context with the keystore
            try {
                secCon = avayaSecurityContext.create(ks);
            } catch (InvalidArgumentException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            }

            // Then we can create a new Avaya CCS client instance to access a AACC server
            // Note that testServer is not a valid server
            avayaClientInstance = Client.create("testServer",
                    avayaProfile,
                    "Avaya Automated Agent Integration",
                    secCon
            );

            // Sign in to the CCS Server
            avayaClientInstance.signin("user", "pw", avayaSessionListener, avayaClientListener);


            CCSSession = avayaClientInstance.getSession();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        */

        this.bot = bot;
        return bot;
    }

    // Create a chat session for the bot
    public Chat createChatSession() {
        Chat chatSession = new Chat(this.bot);
        // Write out note stats to console
        this.bot.brain.nodeStats();
        this.chatSession = chatSession;
        return chatSession;
    }

    // Create a chat session for the bot
    public Chat createChatSession(Bot b) {
        Chat chatSession = new Chat(b);
        // Write out note stats to console
        b.brain.nodeStats();
        this.chatSession = chatSession;
        return chatSession;
    }

    public String generateResponse(String query) {
        String response = "";
        if ((query == null) || (query.length() < 1)) {
            query = MagicStrings.null_input;
        }
        else {
            System.out.println(query);
            String request = query;
            // Uses Alice Bot to find an appropriate response from either the AIML files, or the default bot files
            response = this.chatSession.multisentenceRespond(request);
            // Tidying up responses from bot due to storing sensitive character in XML
            while (response.contains("&lt;")) {
                response = response.replace("&lt;", "<");
            }
            while (response.contains("&gt;")) {
                response = response.replace("&gt;", ">");
            }
        }
        return response;
    }

    // This function just returns the resources folder for the bot
    // It contextually determines it based on the operating system.
    public static String getResourcesPath() {
        File currDir = new File(".");
        String path = currDir.getAbsolutePath();
        path = path.substring(0, path.length() - 2);
        //System.out.println(path);
        //String resourcesPath = path + File.separator + "src" + File.separator + "main" + File.separator + "resources";
        String resourcesPath = path + File.separator + "src";
        System.out.println(resourcesPath);
        return resourcesPath;
    }
}