package io.github.agentsoz.bdimatsim;

/*
 * #%L
 * BDI-ABM Integration Package
 * %%
 * Copyright (C) 2014 - 2015 by its authors. See AUTHORS file.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import io.github.agentsoz.bdiabm.ABMServerInterface;
import io.github.agentsoz.bdiabm.BDIServerInterface;
import io.github.agentsoz.bdiabm.data.AgentDataContainer;
import io.github.agentsoz.bdimatsim.moduleInterface.data.SimpleMessage;
import io.github.agentsoz.dataInterface.DataServer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimUtils;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkChangeEvent.ChangeType;
import org.matsim.core.network.NetworkChangeEvent.ChangeValue;
import org.matsim.core.network.NetworkChangeEventFactory;
import org.matsim.core.network.NetworkChangeEventFactoryImpl;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provider;

/**
 * @author QingyuChen, KaiNagel
 */
public final class MATSimModel implements ABMServerInterface {
	final Logger logger = LoggerFactory.getLogger("");

	private Scenario scenario ;

	private DataServer dataServer;
	private List<SimpleMessage> newVisualiserMessages;

	private List<Id<Person>> bdiAgentIDs;

	private double[] linkCoordsAsArray; //Locations of the links
	private double[] boundingBox; 
	private double time;
	private boolean bdiResponse;

	private final MatsimParameterHandler matSimParameterManager;
	private MatsimAgentManager agentManager ;
	private MobsimDataProvider mobsimDataProvider = new MobsimDataProvider() ;

	final BDIServerInterface bdiServer;

	final Replanner getReplanner() {
		return agentManager.getReplanner() ;
	}

	final MatsimAgentManager getAgentManager() {
		return agentManager;
	}
	@Override
	public final void takeControl(AgentDataContainer agentDataContainer){
		/*Return Control to Matsim here*/
		this.bdiResponse = true ;	
	}
	final void setTime(double time) {
		this.time = time;
	}

	final synchronized void addExternalEvent(@SuppressWarnings("unused") String type,SimpleMessage newEvent){
		//This does mean that the visualiser should be registered very early or events may be thrown away
		if(dataServer != null) newVisualiserMessages.add(newEvent);
	}

	final List<Id<Person>> getBDIAgentIDs(){
		return bdiAgentIDs;
	}

	final Map<Id<Person>,MobsimAgent> getAgentMap(){
		return this.mobsimDataProvider.getAgents() ;
	}

	final Scenario getScenario() {
		return this.scenario ;
	}

	final MobsimDataProvider getMobsimDataProvider() {
		return this.mobsimDataProvider ;
	}

	public MATSimModel( BDIServerInterface bidServer, MatsimParameterHandler matsimParams) {
		this.bdiServer = bidServer ;
		this.matSimParameterManager = matsimParams ;
		this.newVisualiserMessages = new ArrayList<SimpleMessage>();
		this.agentManager = new MatsimAgentManager( this ) ;
	}

	public final void run(String parameterFile,String[] args) {
		// (this needs to be public)

		if (parameterFile != null) {
			matSimParameterManager.readParameters(parameterFile);
		}
		Config config = ConfigUtils.loadConfig( args[0] ) ;

		config.network().setTimeVariantNetwork(true);

		// Normally, the qsim starts at the earliest activity end time.  The following tells the mobsim to start
		// at 2 seconds before 6:00, no matter what is in the initial plans file:
		config.qsim().setStartTime( 1.00 );
		config.qsim().setSimStarttimeInterpretation( QSimConfigGroup.ONLY_USE_STARTTIME );
		//config.qsim().setEndTime( 8.*3600 + 1800. );

		config.controler().setWritePlansInterval(1);
		config.planCalcScore().setWriteExperiencedPlans(true);
		
		config.controler().setOverwriteFileSetting( OverwriteFileSetting.overwriteExistingFiles );

		// ---

		scenario = ScenarioUtils.loadScenario(config) ;

		// this is some conversion of nice matsim objects into ugly conventional data structures :-):
		final Collection<Id<Link>> allLinkIDs = this.getScenario().getNetwork().getLinks().keySet() ;
		this.matSimParameterManager.setNETWORKIDS(Utils.createFlatLinkIDs(allLinkIDs)); //Link IDs in string[] format

		final Collection<? extends Link> links = this.getScenario().getNetwork().getLinks().values();
		this.linkCoordsAsArray = Utils.generateLinkCoordsAsArray(links);
		this.boundingBox = Utils.computeBoundingBox(links);

		this.bdiAgentIDs = Utils.getBDIAgentIDs( scenario, matSimParameterManager );

		// ---

		final Controler controller = new Controler( scenario );

		final EventsManager eventsManager = controller.getEvents();
		eventsManager.addHandler(new AgentActivityEventHandler(MATSimModel.this));

		// having anonymous classes stuck into each other is not very pleasing to read, but it makes for short code and
		// avoids going overboard with passing object references around. kai, mar'15  & may'15
		controller.addOverridingModule(new AbstractModule(){
			@Override public void install() {
				this.bindMobsim().toProvider(new Provider<Mobsim>(){
					@Override
					public Mobsim get() {
						QSim qSim = QSimUtils.createDefaultQSim(scenario, eventsManager) ;

						// ===
						qSim.addQueueSimulationListeners( new MobsimBeforeSimStepListener() {
							boolean setupFlag = true ;
							/**
							 * The most important method - called each time step during the iteration
							 */		
							@Override
							public void notifyMobsimBeforeSimStep(MobsimBeforeSimStepEvent e) {
								MATSimModel.this.setTime(e.getSimulationTime());
								if(setupFlag) {
									// should be possible to do the initialization in some other way ...
									
									//Set up agents
									Utils.initialiseVisualisedAgents(MATSimModel.this) ;

									for(Id<Person> agentId: MATSimModel.this.getBDIAgentIDs()) {
										/*Important - add agent to agentManager */
										MATSimModel.this.getAgentManager().createAndAddBDIAgent(agentId);
										MATSimModel.this.getAgentManager().getReplanner().removeActivities(agentId);
									}
									setupFlag = false;
								}
								// the real work is done here:
								MATSimModel.this.runBDIModule();
							}
						} ) ; // end anonymous class MobsimListener
						// ===
						
						// passes important matsim qsim functionality to the agent manager:
						agentManager.setUpReplanner(qSim);
						// yy "qSim" is too powerful an object here. kai, mar'15

						// add stub agent to keep simulation alive:
						Id<Link> dummyLinkId = qSim.getNetsimNetwork().getNetsimLinks().keySet().iterator().next() ;
						MobsimVehicle veh = null ;
						StubAgent stubAgent = new StubAgent(dummyLinkId,Id.createPersonId("StubAgent"),veh);
						qSim.insertAgentIntoMobsim(stubAgent);

						// return qsim (this is a factory)
						return qSim ;
					}
				}) ;
			}
		});
		

		controller.getMobsimListeners().add(mobsimDataProvider);

		this.bdiServer.init(this.agentManager.getAgentDataContainer(),
				this.agentManager.getAgentStateList(), this,
				Utils.getPersonIDsAsArray(this.bdiAgentIDs));
		
		this.bdiServer.start();
		// (yy "this" is too powerful an object here, but it saves many lines of code. kai, mar'15)

		controller.run();
	}

	final void runBDIModule(){
		this.bdiResponse = false;
		Calendar cal = Calendar.getInstance();
		cal.getTime();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss:SS");

		if(dataServer != null){
			synchronized(this) {
				//visualiser.takeControl(null,newVisualiserMessages.toArray(sm));
				dataServer.stepTime();
				dataServer.publish( "matsim_agent_updates", newVisualiserMessages.toArray(new SimpleMessage[newVisualiserMessages.size()]) );
				newVisualiserMessages.clear();
			}
		}

		this.bdiServer.takeControl(agentManager.getAgentDataContainer());
		// (give control to bdiServer)


		// example how to set the freespeed of some link to zero:
		if ( this.time == 6.*3600. + 10.*60. ) {
			NetworkChangeEventFactory cef = new NetworkChangeEventFactoryImpl() ;
			NetworkChangeEvent event = cef.createNetworkChangeEvent( this.time ) ;
			event.setFreespeedChange(new ChangeValue( ChangeType.ABSOLUTE,  0. ));
			event.addLink( scenario.getNetwork().getLinks().get( Id.createLinkId( 6876 )));
			((NetworkImpl)scenario.getNetwork()).addNetworkChangeEvent(event);

			for ( MobsimAgent agent : this.getAgentMap().values() ) {
				if ( !(agent instanceof StubAgent) ) {
					this.getReplanner().reRouteCurrentLeg(agent, time);
				}
			}
		}

		//Wait for Server to respond
		while (!this.bdiResponse);
		// (framework eventually says "MATSimModel.takeControl()", which sets this.bdiResponse to true)

		logger.trace("Received {}", agentManager.getAgentDataContainer());
		agentManager.updateActions(agentManager.getAgentDataContainer());
	}

	@Override
	public final Object[] queryPercept(String agentID, String perceptID) {

		if (perceptID == MatsimPerceptList.REQUESTLOCATION){
			return agentManager.getLocation(agentID, perceptID);
		}
		else if (perceptID == MatsimPerceptList.SETUPPARAMETERS){
			return new Object[] {matSimParameterManager.getNETWORKIDS(), linkCoordsAsArray, boundingBox };
		}
		else{
			return null;
		}
	}

	public final void registerDataServer( DataServer server ) {
		dataServer = server;
	}

	// the following seems (or is?) a bit confused since different pieces of the program are accessing different containers.  Seems
	// that agentManager maintains the list of BDI agents, while mobsimDataProvider maintains a list of the matsim agents.  
	// The former is a subset of the latter. But it is not so clear if this has to matter, i.e. should there really be two lists, or
	// just one (like a centralized ldap server)? kai, mar'15

	final MATSimAgent getBDIAgent(Id<Person> personId ) {
		return this.agentManager.getAgent( personId ) ;
	}

	final MATSimAgent getBDIAgent(String agentID) {
		return this.agentManager.getAgent( agentID ) ;
	}

	final Map<Id<Person>, MobsimAgent> getMobsimAgentMap() {
		return this.mobsimDataProvider.getAgents() ;
	}

	double getTime() {
		return time ;
	}

}