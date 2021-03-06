package adx.server;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import adx.agent.OfflineAgent;
import adx.exceptions.AdXException;
import adx.messages.ACKMessage;
import adx.messages.ConnectServerMessage;
import adx.structures.BidBundle;
import adx.util.Logging;
import adx.util.Pair;
import adx.util.Startup;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

/**
 * A simple server for the AdX game.
 * 
 * @author Enrique Areyan Viqueira
 */
abstract public class OfflineGameServerAbstract {

  /**
   * Maps that contains all the connections.
   */
  protected Map<String, OfflineAgent> namesToConnections;
  protected Map<OfflineAgent, String> connectionsToNames;

  /**
   * A boolean that indicates whether the server accepts new players.
   */
  protected boolean acceptingNewPlayers;

  /**
   * Current game.
   */
  protected int gameNumber = 1;

  /**
   * An object that maintains the state of the server.
   */
  protected ServerState serverState;

  /**
   * Server constructor.
   * 
   * @param port
   * @throws IOException
   * @throws AdXException 
   */
  public OfflineGameServerAbstract() throws IOException, AdXException {

    Logging.log("[-] Server Initialized at " + Instant.now());
    this.namesToConnections = new ConcurrentHashMap<>();
    this.connectionsToNames = new ConcurrentHashMap<>();
    this.acceptingNewPlayers = true;
    this.serverState = new ServerState(this.gameNumber);
  }
  
  public void receiveMessage(OfflineAgent agent, Object message) {
	  try {
          if (message instanceof ConnectServerMessage) {
            handleJoinGameMessage((ConnectServerMessage) message, agent);
          } else if (message instanceof BidBundle) {
            handleBidBundleMessage((BidBundle) message, agent);
          } else {
            Logging.log("[x] Received an unknown message from " + agent + ", here it is " + message);
          }
        } catch (Exception e) {
          Logging.log("An exception occurred while trying to parse a message in the server");
          e.printStackTrace();
        }
  }
  
  // A better way is to have all the credentials in memory once at startup
  protected synchronized boolean areAgentCredentialsValid(String agentName, String agentPassword) {
//    // For development purposes, this map contains the allowable agents
//    // along with their passwords. This should be obtained from a database.
//    HashMap<String, String> agentsInfo = new HashMap<String, String>();
//    // Dummies
//    for (int i = 1; i < 21; i++) {
//      agentsInfo.put("Agent" + i, "123456");
//    }
//    return agentsInfo.containsKey(agentName) && agentsInfo.get(agentName).equals(agentPassword);
	  if (agentName.length() == 0) {
		  Logging.log("WARNING: trying to enter agent with no name. Connection will be rejected.");
		  return false;
	  }
	  return true;
  }
  
  /**
   * Handles a Join Game message.
   * 
   * @param joinGameMessage
   * @param agentConnection
   * @throws Exception
   */
  protected synchronized void handleJoinGameMessage(ConnectServerMessage joinGameMessage, OfflineAgent agentConnection) throws Exception {
    if (!this.acceptingNewPlayers) {
      joinGameMessage.setServerResponse("Not accepting agents");
      joinGameMessage.setStatusCode(3);
      agentConnection.receiveMessage(joinGameMessage);
      return;
    }
    String agentName = joinGameMessage.getAgentName();
    String agentPassword = joinGameMessage.getAgentPassword();
    Logging.log("\t[-] Trying to register agent: " + agentName + ", with password: " + agentPassword);
    String serverResponse;
    int statusCode;
    if (this.namesToConnections.containsKey(agentName)) {
      Logging.log("\t\t[x] Agent " + agentName + " is already registered");
      serverResponse = "Already Registered";
      statusCode = 0;
    } else if (areAgentCredentialsValid(agentName, agentPassword)) {
      Logging.log("\t\t[-] Agent credentials are valid, agent registered");
      this.namesToConnections.put(agentName, agentConnection);
      this.connectionsToNames.put(agentConnection, agentName);
      this.serverState.registerAgent(agentName);
      serverResponse = "OK";
      statusCode = 1;
    } else {
      Logging.log("\t\t[-] Could not register agent: credentials are not valid");
      serverResponse = "Invalid Credentials";
      statusCode = 2;
    }
    joinGameMessage.setServerResponse(serverResponse);
    joinGameMessage.setStatusCode(statusCode);
    agentConnection.receiveMessage(joinGameMessage);
  }  

  /**
   * Handles a BidBundle message.
   * 
   * @param bidBundle
   * @param connection
   */
  protected void handleBidBundleMessage(BidBundle bidBundle, OfflineAgent connection) {
    Logging.log("[-] Received the following bid bundle: \n\t " + bidBundle + ", from " + this.connectionsToNames.get(connection));
    Pair<Boolean, String> bidBundleAccept = this.serverState.addBidBundle(bidBundle.getDay(), this.connectionsToNames.get(connection), bidBundle);
    if (bidBundleAccept.getElement1()) {
      connection.receiveMessage(new ACKMessage(true, "Bid bundle for day " + bidBundle.getDay() + " received OK."));
    } else {
      connection.receiveMessage(new ACKMessage(false, "Bid bundle for day " + bidBundle.getDay() + " not accepted. Server replied: " + bidBundleAccept.getElement2()));
    }
  }
  
  /**
   * Runs the game. Must be implemented by the extending class.
   * @throws AdXException 
   */
  abstract public void runAdXGame() throws AdXException;
  
}
