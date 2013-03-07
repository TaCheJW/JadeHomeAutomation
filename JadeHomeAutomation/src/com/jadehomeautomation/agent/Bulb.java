package com.jadehomeautomation.agent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;
import jade.proto.AchieveREResponder;

import java.io.IOException;
import java.util.Date;
import java.util.Vector;

import com.jadehomeautomation.agent.HomeAutomation;
import com.jadehomeautomation.message.MeshNetToDeviceMessage;
import com.jadehomeautomation.message.Message;
import com.jadehomeautomation.message.RegistrationMessage;

public class Bulb extends Agent {
	
	/** The bulb is switched on if true */
	private boolean bulbState;
	
	/** Room of the device */
	private AID roomAID;
	
	private String id;
	private String roomId;
	private String name;
	private String description;
	
	/** The MeshNet ID of the device where this bulb is present */
	private int meshnetDeviceId;
	
	
	/** The ID of the device in MeshNet network where there is this light bulb */
	private int meshnetDeviceId;
	
	@Override
	protected void setup() {		
		this.roomAID = null;
		
		Object[] args = getArguments();
		if (args != null) {
			if (args.length > 0) this.id = (String) args[0]; 
			if (args.length > 1) this.roomId = (String) args[1]; 
			if (args.length > 2) this.name = (String) args[2]; 
			if (args.length > 3) this.description = (String) args[3];
			if (args.length > 4) this.meshnetDeviceId = Integer.parseInt((String) args[4]);
			System.out.println("Created Bulb with id "+ this.id +" name " + this.name + " descr " + this.description);
		}
		
		// Register the device to a room
		
		/*
		 * Check every 5 seconds a room to couple with..
		 */
		addBehaviour(new TickerBehaviour(this, 5000) {
			
			@Override
			protected void onTick() {
				log("Trying to get the list of room Agents to couple with...");
				
				ServiceDescription sd = new ServiceDescription();
				sd.setType(HomeAutomation.SERVICE_ROOM_DEVICE_REGISTRATION);
				
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
			
					for(AID aid : agents ){
						log("Added receiver "+aid);
						req.addReceiver(aid);
					}
					
					Message mess = new Message(HomeAutomation.SERVICE_ROOM_DEVICE_REGISTRATION, getAID());
					try {
						req.setContentObject(mess);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
					RegistrationMessage regMessage = new RegistrationMessage(HomeAutomation.SERVICE_ROOM_DEVICE_REGISTRATION, getAID(), roomId, name, description);
					try {
						req.setContentObject(regMessage);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					// Timeout is 10 seconds.
					req.setReplyByDate(new Date(System.currentTimeMillis() + 10000));

					//
					AchieveREInitiator reInitiator = new AchieveREInitiator(this.myAgent, req){
						protected void handleInform(ACLMessage inform) {
							log("Agent "+inform.getSender().getName()+" successfully performed the requested action");
							
							roomAID = inform.getSender();
							
							// start listening requests..
							registerDevice();
							
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
	 * Method that register the device agent to the df and defines requests managment  
	 */
	protected void registerDevice() {
		log("I'm started.");

		// Create the agent description.
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		
		// Create the service description.
		ServiceDescription sd = new ServiceDescription();
		sd.setType(HomeAutomation.SERVICE_BULB_CONTROL);
		sd.setName("JADE-bulb-control");
		
		// Add the service description to the agent description.
		dfd.addServices(sd);
		try {
			// Register the service (through the agent description multiple
			log("Registering '"+sd.getType()+"' service named '"+sd.getName()+"'" + "to the default DF...");
			
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
				
				log("Handle request..");

				try {
					
					switchBulb(!bulbState, myAgent);
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ACLMessage res = new ACLMessage(ACLMessage.AGREE); 
				return res;
			}
			
			@Override
			protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response)
				throws FailureException{
				
				log("Prepare result");
				
				return new ACLMessage(ACLMessage.INFORM);
			}
		});
	}
	
	
	/**
	 * Change bulb state.
	 * 
	 * This method contacts a MeshNetGateway agent to perform the action by
	 * sending a message to the device via the MeshNet network.
	 */
	private void switchBulb(boolean bulbNewState, Agent myAgent) throws IOException{
		
		log("Trying to switch bulb ...");
	
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType(MeshNetGateway.SEND_TO_DEVICE_SERVICE);
		template.addServices(sd);
		
		SearchConstraints all = new SearchConstraints();
		all.setMaxResults(new Long(-1));
		
		DFAgentDescription[] result = null;
		try {
			log("Searching '"+sd.getType()+"' service in the default DF...");

			result = DFService.search(myAgent, template, all);
			AID[] agents = new AID[result.length];

			ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
			req.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
			
			for(int i = 0; i < agents.length; i++){
				agents[i] = result[i].getName();
				log("Agent '"+agents[i].getName()+"' found.");

				log("Sending REQUEST for register rooms to building.. '"+
						agents[i].getName()+"'...");
				req.addReceiver(agents[i]);
			}

			// This is the low level MeshNet message data needed to switch the led on the target device
			byte[] data = new byte[1];
			if(bulbNewState){
				data[0] = 1;
			} else {
				data[0] = 0;
			}
			MeshNetToDeviceMessage msg = new MeshNetToDeviceMessage(meshnetDeviceId, data, 1);
			
			req.setContentObject(msg);

			// Timeout is 10 seconds.
			req.setReplyByDate(new Date(System.currentTimeMillis() + 10000));

			AchieveREInitiator reInitiator = new AchieveREInitiator(myAgent, req){
				protected void handleInform(ACLMessage inform) {
					log("Agent "+inform.getSender().getName()+" successfully performed the requested action");

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

		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		
		log("Bulb switched, on="+bulbNewState);
		bulbState=bulbNewState;
		
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
