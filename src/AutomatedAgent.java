import com.avaya.ccs.api.*;
import com.avaya.ccs.api.enums.*;
import com.avaya.ccs.api.exceptions.ObjectInvalidException;
import com.avaya.ccs.core.*;
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


import org.json.JSONObject;


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
        LOG.info("AvayaClientListener", new Object[]{"Client event found: " + clientEvent});
        getCcsServerInfo();
    };

    UserListenerI avayaUserListener = userEvent -> {
    	// Notifies Java Client of incoming CCS user updates

		// Look for agentLogin return
		// call ready function here

		System.out.println("User response data:"  + userEvent.getResponseData());
        System.out.println("User event found: " + userEvent);

        LOG.info("AvayaUserListener", new Object[]{"User event found: " + userEvent});

        // Update local user instance
		try {
			setUser(client.getSession().findUser(properties.getProperty("agentID")));
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		} catch (ObjectInvalidException e) {
			e.printStackTrace();
		}
	};

    // This sends information from the session to the server
    // These listeners need to be set up correctly
    SessionListenerI avayaSessionListener = sessionEvent -> {	
    	// This automatically updates the local declaration of the session with the instance sent to the client by the CCS Server
        System.out.println("Session event found - updating session accordingly");
        LOG.info("AvayaUserListener", new Object[]{"Session event found - updating session accordingly"}); 
        setSession(getClient().getSession());
    };

    ResourceListenerI avayaResourceListener = resourceEvent -> {
        System.out.println("Resource event found: " + resourceEvent);
        LOG.info("AvayResourceListener", new Object[]{"Resource event found: " + resourceEvent});
    };

    CustomerListenerI avayaCustomerListener = nei -> {
    	System.out.println("Customer event found: " + nei);
    	LOG.info("AvayaCustomerListener", new Object[]{"Customer event found: " + nei});
    };
    
    // These interaction listener functions are used to update the local instances of their respective objects

	// Call answerCall function then
    InteractionListenerI avayaInteractionListener = new InteractionListenerI() {
    	// This handles any interaction events
        @Override
        public void onInteractionEvent(NotificationEventI<InteractionI> interactionEvent) {
        	// If the interaction event is a list of destinations, we want to update the local declaration
        	// With the incoming list
        	if(interactionEvent.getResponseData() instanceof DestinationListI) {
        		System.out.println("Destination list received, updating local instance");
        		LOG.info("AvayaInteractionListener - onInteractionEvent", new Object[]{"Destination list received, updating local instance"});
        		setConsultantDestinations((DestinationListI) interactionEvent.getResponseData());
        	}
        	// Otherwise, we want to update the local Interaction object instead
			//TODO: LaunchState=ALERTING
        	else {
	            System.out.println("Interaction event found - attempting to update Interaction object");
        		LOG.info("AvayaInteractionListener - onInteractionEvent", new Object[]{"Interaction event found - attempting to update Interaction object"});
        		System.out.println("Interaction event: " + interactionEvent);
	            // null local instance if a delete notification is sent in
	            if(interactionEvent.getNotificationType()== NotificationType.DELETE) {
	            	System.out.println("Interaction deleted");
	            	LOG.info("AvayaInteractionListener - onInteractionEvent", new Object[]{"Interaction Deleted"});
	            	//setInteraction(null);
	            }
	            try {
	            	// Get the interaction from the setting, and assign it to the local variable
	                LOG.debug("AvayaInteractionListener - onInteractionEvent", new Object[]{"Assigning interaction locally..."});
	                System.out.println("Assigning interaction...");
	            	setInteraction(getSession().getInteractions().get(0));
	            	// TODO: Look at right interaction state
					// If an incoming interaction has an 'Active' or
					answerWebChat();
	            	if (getInteraction().getState() == InteractionState.Active){
	            		System.out.println("Interaction is ringing, attempting to answer");
	            		answerWebChat();
					}
	            } catch (ObjectInvalidException e) {
	                e.printStackTrace();
	            } catch (NullPointerException e){
	                System.out.println("No Interaction found in the session");
	            	LOG.info("AvayaInteractionListener - onInteractionEvent", new Object[]{"No Interaction found in the session"});

	            }
        	}
        }

        @Override
        public void onInteractionMediaEvent(NotificationEventI<? extends MediaI> nei) {
            System.out.println("Interaction media event found:" + nei);
            // We want to update the local web chat instance only if we get one incoming
			//TODO: Answering webchat here
            if(nei.getNotificationObject().getContactType() == ContactType.Web_Communications){
            	System.out.println("Incoming web communication, updating media class");
            	setChat((WebChatMedia) getInteraction().getMedia());
            	answerWebChat();
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

    public void clientDance(){
    	int counter = 0;
    	// Create a new client and log it into the ccs server
    	// Create a new instance of the client
        setClient(
                Client.create(
                        properties.getProperty("server"),
                        Profile.AgentDesktop,
                        "AutomatedAgentTesting",
                        null
                )
        );
        System.out.println("Initial client state: " + getClient().getState());
        // Continue to try and sign in the client until it is authenticated
        while(getClient().getState() != ClientState.AUTHENTICATED) {
	        try {
	        	counter++;
	            getClient().signin(
	            		properties.getProperty("clientID"),
	            		properties.getProperty("clientPassword"),
	                    avayaSessionListener,
	                    avayaClientListener
	            );
	            TimeUnit.SECONDS.sleep(5);
	            LOG.debug("ClientDance", new Object[]{"Client is now: " + getClient().getState()});
	            System.out.println("Client is now: " + getClient().getState());
	        } catch (InvalidArgumentException e) {
	            e.printStackTrace();
	        } catch (InvalidStateException e) {
	            e.printStackTrace();
	            try{
	            	TimeUnit.SECONDS.sleep(5);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
	        	if (counter > 10){
	        		System.out.println("Error: maximum connection attempts reached, exiting program");
					LOG.error("ClientDance", new Object[]{"Error: maximum connection attempts reached, exiting program"});

					System.exit(0);
				}
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
        	// Get the user associated with the agent ID
            setUser(client.getSession().findUser(properties.getProperty("agentID")));
			while(getUser().getState() == UserState.LoggedOut) {
				System.out.println("Logging user in...");
				getUser().login(properties.getProperty("agentPassword"), null);
			}

            while( !(getUser().getState().equals(UserState.Ready)) && !(getUser().getState().equals(UserState.ReadyAfterCall))) {
				System.out.println("User state: " + getUser().getState());
				System.out.println("User can login: " + getUser().canLogin());
				// login the user
				getUser().login(properties.getProperty("agentPassword"), null);
				TimeUnit.SECONDS.sleep(5);
				// Ready the user
				getUser().ready();
			}
        }
        catch (ObjectInvalidException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
        	e.printStackTrace();
        } catch (InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

    public void answerWebChat() {
    	// Attempt to answer a call from an incoming customer
    	Boolean transferFlag = false;
    	//System.out.println("Interaction: " + getInteraction().getState());
    	while(true) {
	        try {
	        	// If the interaction can be answered or the state is active, and the transfer flag has not been set
	            if ((getInteraction().canAnswer() || getInteraction().getState()==InteractionState.Active) && transferFlag==false) {
		        //if ((getInteraction().canAnswer() && transferFlag==false)) {
	                // Answer incoming call
					getInteraction().answer();
					System.out.println("Can open media: " + getInteraction().canOpenMedia());

					// While the media can be opened
					while(!(getInteraction().canOpenMedia())) {
						getInteraction().getMedia();
						getCcsServerInfo();

						// Check for media
						System.out.println("Can open media: " + getInteraction().canOpenMedia());
						System.out.println("media status: " + getInteraction().getMedia());


						// Open chat for call
						getInteraction().openMedia();

						TimeUnit.SECONDS.sleep(8);
					}
					// Define info for rasa bot (full name or unique identifier)
	                // TODO: get information from interaction
					// TODO: Get inter
	                String customerName = getInteraction().getId();

                    String botMessage = "";



					// If getSenderType == agent, ignore message
					while(true) {
						//while (getChat().getSenderType() != WebChatSenderType.agent) {
						while(true){
							    // TODO: Change from checking that message contains transfer
							    if (botMessage.contains("transferring")) {
							        String skillset = jsonPostRequest(String.format("http://localhost:5005/conversations/%s/story", customerName), "");
                                    System.out.println("Skillset: " + skillset);
							        transferFlag = true;
                                    getChat().sendMessage("Ok, transferring you to a human agent...", false);
                                    transferCall(skillset);
                                    TimeUnit.SECONDS.sleep(5);
                                    break;
							    }
							    else {
							        //System.out.println(getChat().getMessage());

                                    // Create API post request to rasa bot
                                    // We create a json body with the username and the message
                                    String httpBody = String.format(
                                            "{\"name\": \"%s\", \"message\": \"%s\"}",
                                            customerName,
                                            getChat().getMessage()
                                    );

                                    botMessage = jsonPostRequest(properties.getProperty("rasaLocation"), httpBody);

                                    // We send a message to the chat service
                                    getChat().sendMessage(botMessage, false);

                                    System.out.println("Sleeping...");
                                    TimeUnit.SECONDS.sleep(4);
                                }
    						}
	    				}
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
    
    public void transferCall(String skillset) {
    	try {
    		// Gets destinations (potential agents to redirect call to) based on skillset
            interaction.getConsultDestinations(DestinationType.Skillset);
            System.out.println(getConsultantDestinations().getDestinations());
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

	public static String jsonGetRequest(String urlToRead) {
		try {
			StringBuilder result = new StringBuilder();
			URL url = new URL(urlToRead);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
			rd.close();

			JSONObject j = new JSONObject(result.toString());
			j = (JSONObject) j.get("slots");

			return (String) j.get("agent_type");

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

}