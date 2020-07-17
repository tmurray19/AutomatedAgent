import org.alicebot.ab.Bot;
import org.alicebot.ab.Chat;

// Creating an interface to outline interactions with alice bot
public interface BotInterface {

    // Create a new bot instance
    public Bot createBot(String botName);

    // Create a new chat instance for a bot
    public Chat createChatSession();

    // Create a new chat instance for a bot with a specific bot
    public Chat createChatSession(Bot b);

    // Generate a response from a query
    public String generateResponse(String query);

}
