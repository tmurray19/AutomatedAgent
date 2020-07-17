import com.avaya.ccs.api.*;
import com.avaya.ccs.api.exceptions.ObjectInvalidException;
import com.avaya.ccs.core.*;
import com.avaya.ccs.api.enums.ClientState;
import com.avaya.ccs.api.enums.ContactType;
import com.avaya.ccs.api.enums.DestinationType;
import com.avaya.ccs.api.enums.InteractionState;
import com.avaya.ccs.api.enums.NotificationType;
import com.avaya.ccs.api.enums.Profile;
import com.avaya.ccs.api.exceptions.InvalidArgumentException;
import com.avaya.ccs.api.exceptions.InvalidStateException;


import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

class AutomatedAgent {

    final Logger LOG = Logger.getLogger(AutomatedAgent.class);

    // Declaration of variables
    UserI u = null;
    WebChatMedia chat = null;
    InteractionI interaction = null;
    AvayaAliceBot bot = null;
    ClientI client = null;
    SessionI session = null;
    DestinationListI destinations = null;
    DestinationI destination = null;
    Properties properties = null;
    
    // Accessors and mutators
    public UserI getUser(){ return this.u; }
    public WebChatMedia getChat(){ return this.chat; }
    public InteractionI getInteraction(){ return this.interaction; }
    public ClientI getClient(){ return this.client; }
    public SessionI getSession(){ return this.session; }
    public DestinationListI getConsultantDestinations() { return this.destinations; }
    public DestinationI getDestination() { return this.destination; }
    public Properties getProperties() { return this.properties; }

    public void setUser(UserI u){ this.u = u;}
    public void setChat(WebChatMedia chat){ this.chat = chat; }
    public void setInteraction(InteractionI interaction){ this.interaction = interaction; }
    public void setClient(ClientI client){ this.client = client; }
    public void setSession(SessionI session){ this.session = session; }
    public void setConsultantDestinations(DestinationListI destinations) { this.destinations = destinations; }
    public void setDestination(DestinationI destination) { this.destination = destination; }
    public void setProperties(Properties properties) { this.properties = properties; }
    
    // Creating listeners for the CCS API to function correctly
    // This sends information from the client to the server
    ClientListenerI avayaClientListener = clientEvent -> {
    	// Notifies Java Client of incoming CCS client updates
        System.out.println("Client event found: " + clientEvent);
        LOG.info("Client event found");
    };

    UserListenerI avayaUserListener = userEvent -> {
    	// Notifies Java Client of incoming CCS user updates
        System.out.println("User event found: " + userEvent);
    };

    // This sends information from the session to the server
    // These listeners need to be set up correctly
    SessionListenerI avayaSessionListener = sessionEvent -> {	
    	// This automatically updates the local declaration of the session with the instance sent to the client by the CCS Server
        System.out.println("Session event found - updating session accordingly");
        setSession(getClient().getSession());
    };

    ResourceListenerI avayaResourceListener = resourceEvent -> {
        System.out.println("Resource event found: " + resourceEvent);
    };

    CustomerListenerI avayaCustomerListener = nei -> {
    	System.out.println("Customer event found: " + nei);
    };
    
    // These interaction listener functions are used to update the local instances of their respective objects
    InteractionListenerI avayaInteractionListener = new InteractionListenerI() {
    	// This handles any interaction events
        @Override
        public void onInteractionEvent(NotificationEventI<InteractionI> interactionEvent) {
        	// If the interaction event is a list of destinations, we want to update the local declaration
        	// With the incoming list
        	if(interactionEvent.getResponseData() instanceof DestinationListI) {
        		System.out.println("Destination list received, updating local instance");
        		setConsultantDestinations((DestinationListI) interactionEvent.getResponseData());
        	}
        	// Otherwise, we want to update the local Interaction object instead
        	else {
	            System.out.println("Interaction event found - attempting to update Interaction object");
	            System.out.println(interactionEvent);
	            // null local instance if a delete notification is sent in
	            if(interactionEvent.getNotificationType()== NotificationType.DELETE) {
	            	System.out.println("Interaction deleted");
	            	//setInteraction(null);
	            }
	            try {
	            	// Get the interaction from the setting, and assign it to the local variable
	                setInteraction(getSession().getInteractions().get(0));
	            } catch (ObjectInvalidException e) {
	                e.printStackTrace();
	            } catch (NullPointerException e){
	                System.out.println("No Interaction found in the session");
	            }
        	}
        }

        @Override
        public void onInteractionMediaEvent(NotificationEventI<? extends MediaI> nei) {
            System.out.println("Interaction media event found:" + nei);
            // We want to update the local web chat instance only if we get one incoming
            if(nei.getNotificationObject().getContactType() == ContactType.Web_Communications){
            	System.out.println("Incoming web communication, updating media class");
            	setChat((WebChatMedia) getInteraction().getMedia());
            }
        }
    };
    
    // Open properties file for reading of information
    public void readProperties() {
    	try {
    		properties = new Properties();
    		properties.load(new FileInputStream("config.properties"));
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }


    //TODO: Implement a new Automated Agent
    //TODO: This bot is now depreciated
    // Rasa is now the default bot solution
    public void createBot(){
        // Create a new bot
        bot = new AvayaAliceBot();
        // Bot needs to be named "Avaya" to get access to memory
        bot.createBot("Avaya");
        // Set up a chat session for the bot
        bot.createChatSession();
    }

    public void clientDance(){
    	// Create a new instance of the client
        setClient(
                Client.create(
                        properties.getProperty("server"),
                        Profile.AgentDesktop,
                        "AutomatedAgentTesting",
                        null
                )
        );
        // Continue to try and sign in the client until it is authenticated
        while(getClient().getState() != ClientState.AUTHENTICATED) {
	        try {
	            getClient().signin(
	            		properties.getProperty("clientID"),
	            		properties.getProperty("clientPassword"),
	                    avayaSessionListener,
	                    avayaClientListener
	            );
	            TimeUnit.SECONDS.sleep(4);
	            System.out.println("Client is now: " + getClient().getState());
	        } catch (InvalidArgumentException e) {
	            e.printStackTrace();
	        } catch (InvalidStateException e) {
	            e.printStackTrace();
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
        }
    }

    public void getCcsServerInfo() {
    	// Query the CCS for the list of users, resources, and interactions associated with the session
        try {
            System.out.println("Getting info from server...");
            
            // Get all info from CCS
            client.getSession().openUsers(avayaUserListener);
            TimeUnit.SECONDS.sleep(1);

            client.getSession().openResources(avayaResourceListener);
            TimeUnit.SECONDS.sleep(1);

            client.getSession().openInteractions(avayaInteractionListener);

            TimeUnit.SECONDS.sleep(3);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        } catch (ObjectInvalidException e) {
            e.printStackTrace();
        }
    }

    // Get a User class from the CCS Server and attempts to log it into the server
    public void userDance() {
        try {
        	// Get the first user associated with the 
            u = client.getSession().getUsers().get(0);
            // login the user 
            u.login(properties.getProperty("agentPassword"), null);
            TimeUnit.SECONDS.sleep(2);
            // Ready the user
            u.ready();
        } catch (ObjectInvalidException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
        	e.printStackTrace();
        }
    }

    public void answerWebChat() {
    	Boolean transferFlag = false;
    	while(true) {
	        try {
	        	// If the interaction can be answered or the state is active, and the transfer flag has not been set
	            if ((getInteraction().canAnswer() || getInteraction().getState()==InteractionState.Active) && transferFlag==false) {
		        //if ((getInteraction().canAnswer() && transferFlag==false)) {
	                // Answer incoming call
	                getInteraction().answer();
	                // Open chat for call
	                getInteraction().openMedia();
	                // Define info for rasa bot (full name or unique identifier)
	                // TODO: get information from interaction
	                String customerName = 
	    	                getInteraction().getCustomerDetails().getId();

	                while (true) {
	                	// TODO: Change from checking that message contains transfer
	                    if(getChat().getMessage().contains("Transfer")) {
	                    	transferFlag = true;
	                    	getChat().sendMessage("Ok, transferring you to a human agent...", false);
	                    	transferCall();
	                    	TimeUnit.SECONDS.sleep(5);
	                    	break;
	                    }
	                    else {
		                    System.out.println(getChat().getMessage());
		                    	
		                    // Create API post request to rasa bot
		                    // We create a json body with the username and the message
		    	            String httpBody =
		    	            	String.format("{\"name\": \"%s\", \"message\": \"%s\"}", customerName, getChat().getMessage());
		    	        
		    	            // We send a message to the chat service
		    	            getChat().sendMessage(
		    	            		// Do this through making a json post request to rasa
		    	            		jsonPostRequest(
		    	            				properties.getProperty("rasaLocation"),
		    	            				httpBody
		    	            				), 
		    	            		false
		    	            		);
		                    //getChat().sendMessage(bot.generateResponse(getChat().getMessage()), false);
		
		                    System.out.println("Sleeping...");
		                    TimeUnit.SECONDS.sleep(4);
	                    }
	                }
	
	
	                //interaction.end();
	            } 
	            if (transferFlag == true) {
	            	System.out.println("Call is being transferred, bot work finished");
	            	break;
	            }
	            else {
	                System.out.println("Can't answer, retrying...");
	                TimeUnit.SECONDS.sleep(2);
	                //answerInteraction();
	            }
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        } catch (ObjectInvalidException e) {
	        	System.out.println("Invalid object");
	            e.printStackTrace();
	            break;
	        } 
	        catch (NullPointerException e){
	            System.out.println("No interaction found, retrying in four seconds");
	            e.printStackTrace();
	            try {
	                TimeUnit.SECONDS.sleep(4);
	            } catch (InterruptedException ex) {
	                ex.printStackTrace();
	            }
	            //answerInteraction();
	        }
	    }
    }
    
    public void transferCall() {
    	try {
    		// Gets destinations (potential agents to redirect call to) based on skillset
            interaction.getConsultDestinations(DestinationType.Skillset);
            // Set the destination locally
            // TODO: current code is getting first consult destination that shows up, needs to change
            setDestination(getConsultantDestinations().getDestinations().get(0));
    		System.out.println("Transferring to destination: " + getDestination());
        	System.out.println("Tranferring call");
        	TimeUnit.SECONDS.sleep(2);
        	// Attempt a transfer to the destination
			getInteraction().transferToDestination(getDestination());
			TimeUnit.SECONDS.sleep(4);
			System.out.println("sleep done");
		} catch (InvalidArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	catch (ObjectInvalidException e) {
    		System.out.println("Invalid object");
    		e.printStackTrace();
    	}
    	catch(InterruptedException e) {
    		e.printStackTrace();
    	}
    }
    
    public String jsonPostRequest(String requestURL, String jsonBody) {
    	// Declare url 
    	URL url;
    	HttpURLConnection conn = null;
    	try {
    		url = new URL (requestURL);
    		// Open connection to url
    		conn = (HttpURLConnection)url.openConnection();
    		// Set the request method to send information to the URL
    		conn.setRequestMethod("POST");
    		conn.setRequestProperty("Content-Type", "application/json; utf-8");
    		conn.setRequestProperty("Accept", "application/json");
    		
    		// enable requests
    		conn.setDoOutput(true);
    		
    		// write request to url
    		
    		OutputStream outStream = conn.getOutputStream();
    		byte[] input = jsonBody.getBytes("utf-8");
    		outStream.write(input, 0, input.length);
    		
    		BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
    		StringBuilder resp = new StringBuilder();
    		String responseLine = null;
    		while((responseLine = br.readLine()) != null) {
    			resp.append(responseLine.trim());
    		}
    		return(resp.toString());
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    	finally {
    		if(conn!= null) {
    			conn.disconnect();
    		}
    	}
    }
    
    public String postRequest(String targetURL, String urlParameters) {
    	// Send a post request to a targetURL, with a body of urlParameters
    	URL url;
    	HttpURLConnection connection = null;
    	try {
    		url = new URL(targetURL);
    		connection = (HttpURLConnection)url.openConnection();
    		connection.setRequestMethod("POST");
    		connection.setRequestProperty("Content-Type", "application/json");
    		connection.setRequestProperty("Content-Length", ""+ Integer.toString(urlParameters.getBytes().length));
    		connection.setRequestProperty("Content-Language", "en-US");
    		
    		connection.setUseCaches(false);
    		connection.setDoInput(true);
    		connection.setDoOutput(true);
    		
    		// Send request
    		DataOutputStream sendRequest = new DataOutputStream(connection.getOutputStream());
    		sendRequest.writeBytes(urlParameters);
    		sendRequest.flush();
    		sendRequest.close();
    		
    		
    		// Read response
    		InputStream inStream = connection.getInputStream();
    		BufferedReader reader = new BufferedReader(new InputStreamReader(inStream));
    		String line;
    		StringBuffer response = new StringBuffer();
    		while((line= reader.readLine()) != null) {
    			response.append(line);
    			response.append('\r');
    		}
    		reader.close();
    		return response.toString();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    	finally {
    		if(connection != null) {
    			connection.disconnect();
    		}
    	}

    }
}

