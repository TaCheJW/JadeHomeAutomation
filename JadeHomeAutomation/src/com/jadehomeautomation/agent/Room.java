package com.jadehomeautomation.agent;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.Vector;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.AchieveREInitiator;
import jade.proto.AchieveREResponder;

public class Room extends Agent {
	// Devices in the room
	private LinkedList<AID> devices;	
	
	// Building of the room
	private AID buildingAID;
	
	@Override
	protected void setup() {						
		log("I'm started.");

		this.devices = new LinkedList<AID>();
		this.buildingAID = null;
		
		// Register room to a building
		
		/*
		 * Check every 5 seconds a building to couple with..
		 */
		addBehaviour(new TickerBehaviour(this, 5000) {
			
			@Override
			protected void onTick() {
				
				
				log("Trying to get the list of Building Agents to couple with...");
				
				//TODO use constant for types..
				ServiceDescription sd = new ServiceDescription();
				sd.setType("building-room-registration");
				
				DFAgentDescription template = new DFAgentDescription();
				template.addServices(sd);

				SearchConstraints all = new SearchConstraints();
				all.setMaxResults(new Long(-1));
				
				DFAgentDescription[] result = null;
				AID[] agents = null;
				
				try {

					log("Searching '"+sd.getType()+"' service in the default DF...");
					
					result = DFService.search(myAgent, template, all);
					agents = new AID[result.length];
					for (int i = 0; i < result.length; ++i) {

						agents[i] = result[i].getName();
						log("Agent '"+agents[i].getName()+"' found.");
					}
				} catch (FIPAException fe) {
					fe.printStackTrace();
				}
				
				if(result.length != 0){
					ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
					req.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
					
					/*
					for (int i = 0; i < agents.length; ++i) {
						log("Sending REQUEST for register rooms to building.. '"+
								agents[i].getName()+"'...");
						req.addReceiver(agents[i]);
					} */
					
					//TODO rethink how to decide which building should be choosen
					log("Sending REQUEST for register rooms to building.. '"+ agents[0].getName()+"'...");
					req.addReceiver(agents[0]);
					
					//TODO add request type with costants or with objects..
					req.setContent("building-room-registration");
					
					/*
					 * Timeout is 10 seconds.
					 */
					req.setReplyByDate(new Date(System.currentTimeMillis() + 10000));

					//
					AchieveREInitiator reInitiator = new AchieveREInitiator(this.myAgent, req){
						protected void handleInform(ACLMessage inform) {
							log("Agent "+inform.getSender().getName()+" successfully performed the requested action");
							
							buildingAID = inform.getSender();
							
							// start listening requests..
							registerRoom();
							
							// stop loocking for building
							stop();
							
						}
						protected void handleRefuse(ACLMessage refuse) {
							log("Agent "+refuse.getSender().getName()+" refused to perform the requested action");
						}
						protected void handleFailure(ACLMessage failure) {
							if (failure.getSender().equals(myAgent.getAMS())) {
								// FAILURE notification from the JADE runtime: the receiver
								// does not exist
								log("Responder does not exist");
							}
							else {
								log("Agent "+failure.getSender().getName()+" failed to perform the requested action");
							}
						}
						protected void handleAllResultNotifications(Vector notifications) {
							log("HandleAllResultNotification");
						}
					};
					
					myAgent.addBehaviour(reInitiator);
				}
			}	
		} );
	}

	/*
	 * Method that register the room agent to the df and defines requests managment
	 */
	protected void registerRoom() {
		// Create the agent description.
				DFAgentDescription dfd = new DFAgentDescription();
				dfd.setName(getAID());
				
				// Create the services description.
				ServiceDescription deviceListSD = new ServiceDescription();
				deviceListSD.setType("room-device-list");
				deviceListSD.setName("JADE-room-device-list");
				
				ServiceDescription deviceRegistrationSD = new ServiceDescription();
				deviceRegistrationSD.setType("room-device-registration");
				deviceRegistrationSD.setName("JADE-room-device-registration");
				
				// Add the services description to the agent description.

				//TODO add here other Sevice Descriptions
				dfd.addServices(deviceListSD);
				dfd.addServices(deviceRegistrationSD);
				
				try {
					// Register the service
					log("Registering '"+deviceListSD.getType()+"' service named '"+deviceListSD.getName()+"'" + "to the default DF...");
					
					DFService.register(this, dfd);
					
					log("Waiting for request...");
				} catch (FIPAException fe) {
					fe.printStackTrace();
				}

				// Add the behaviour serving queries from controller agents.
				 
				MessageTemplate template = AchieveREResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_REQUEST);
				addBehaviour(new AchieveREResponder(this, template){
					
					@Override
					protected ACLMessage handleRequest(ACLMessage request) 
						throws NotUnderstoodException, RefuseException{
						
						log("Handle request with content:" + request.getContent());
						return new ACLMessage(ACLMessage.AGREE);
					}
					
					@Override
					protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response){
						
						log("Prepare result notification with content: " + request.getContent());
						response.setPerformative(ACLMessage.INFORM);
						
						//TODO check request type with costants or with objects..
						if (request.getContent().equals("room-device-list")) {
							try {
								
								/*
								//TODO remove followings lines.. only test..
								
								// send test array...
								LinkedList<AID> aids = new LinkedList<AID>();
								
								AID device1 = new AID("device001");
								aids.add(device1);

								AID device2 = new AID("device002");
								aids.add(device2);
								
								response.setContentObject(aids);
								*/
								
								// send device array
								response.setContentObject(devices);
								
							} catch (IOException e) {
								e.printStackTrace();
							}					
						}
						else if(request.getContent().equals("room-device-registration")){
							
							log("Adding device " + request.getSender() + "to device list...");
							
							devices.add(request.getSender());
							
							log("Device " + request.getSender() + " successfully added to room's device list.");
							
						}
						
						return response;
					}
				});
	}
	
	/*
	 * Remember to deregister the services offered by the agent upon shutdown,
	 * because the JADE platform does not do it by itself!
	 */
	@Override
	protected void takeDown() {
		try {
			log("De-registering myself from the default DF...");
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		log("I'm done.");
	}
		
	private void log(String msg) {
		System.out.println("["+getName()+"]: "+msg);
	}
}