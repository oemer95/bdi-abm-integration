package io.github.agentsoz.conservation;

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
import io.github.agentsoz.bdiabm.data.AgentDataContainer;
import io.github.agentsoz.bdiabm.data.AgentStateList;
import io.github.agentsoz.conservation.jill.agents.Landholder;

import java.util.Random;

import io.github.agentsoz.abmjill.JillModel;

/**
 * Manages the land holders (the BDI system)
 * 
 * @author Sewwandi Perera
 */
public class LandholderModel extends JillModel {
	/**
	 * This random generator is used when dicing each agent's Conservation Ethic
	 * barometer.
	 */
	private Random randomCGenerator;

	/**
	 * This random generator is used when dicing each agent's Profit motive
	 * barometer.
	 */
	private Random randomPGenerator;

	/**
	 * How many agents (land holders) have high Conservation Ethic.
	 */
	private static int highConsevationAgents;

	/**
	 * Public constructor
	 */
	public LandholderModel() {
		super();
		randomCGenerator = new Random(
				ConservationUtils.getConservationEthicSeed());
		randomPGenerator = new Random(
				ConservationUtils.getProfitMotivationSeed());
		highConsevationAgents = -1;
	}

	/**
	 * Returns a random value which is: higher than or equal to the middle point
	 * of the conservation ethic barometer's range, less than or equal to the
	 * upper margin conservation ethic barometer's range.
	 * 
	 * @return
	 */
	private double getHighCoservationEthic() {
		return ConservationUtils.getMaxConservationEthic() / 2
				* (1 + randomCGenerator.nextDouble());
	}

	/**
	 * Returns a random value greater than or equals to zero but less than the
	 * middle point of the conservation ethic barometer's range
	 * 
	 * @return
	 */
	private double getLowCoservationEthic() {
		return (ConservationUtils.getMaxConservationEthic() / 2)
				* randomCGenerator.nextDouble();
	}

	/**
	 * Initialise {@link LandholderModel}
	 */
	@Override
	public boolean init(AgentDataContainer agentDataContainer,
			AgentStateList agentList, ABMServerInterface abmServer,
			Object[] params) {
		// Call the integration level initialisation first
		boolean result = super.init(agentDataContainer, agentList, abmServer,
				params);
		// Now do any app level initialisations here
		if (highConsevationAgents == -1) {
			highConsevationAgents = (int) Math.round(ConservationUtils
					.getHighCEAgentsPercentage() * Main.numLandholders() / 100);
		}
		// Finally call the init on all the agents

		for (int i = 0; i < Main.numLandholders(); i++) {
			Landholder landholder = (Landholder) getAgent(i);
			landholder.setName(Integer.toString(i + 1));
			if (highConsevationAgents > 0) {
				landholder.init(randomPGenerator.nextDouble()
						* ConservationUtils.getMaxProfitMotivation(),
						getHighCoservationEthic(), true);
				highConsevationAgents--;
			} else {
				landholder.init(randomPGenerator.nextDouble()
						* ConservationUtils.getMaxProfitMotivation(),
						getLowCoservationEthic(), false);
			}
		}

		// Test
		int numHighCEAgents = 0;
		for (int i = 0; i < Main.numLandholders(); i++) {
			Landholder landholder = (Landholder) getAgent(i);
			if (landholder.isConservationEthicHigh()) {
				numHighCEAgents++;
			}
		}
		if (Math.round(ConservationUtils.getHighCEAgentsPercentage()
				* Main.numLandholders() / 100) != numHighCEAgents) {
			Log.error("High CE Agents Percentage is not correctly set. Configured:"
					+ ConservationUtils.getHighCEAgentsPercentage()
					+ " Found:"
					+ numHighCEAgents);
			System.exit(0);
		}
		return result;
	}
}