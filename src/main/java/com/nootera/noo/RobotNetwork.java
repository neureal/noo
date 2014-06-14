/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.noo;

import java.util.Arrays;
import java.util.List;
import org.encog.ml.MLMethod;
import org.encog.ml.MLRegression;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.temporal.TemporalPoint;
import org.encog.neural.networks.BasicNetwork;

public class RobotNetwork {

	private final MLMethod method;
	private final Robot actor;
	private final boolean log;

	public RobotNetwork(MLMethod method) {
		this.method = method; this.actor = null; this.log = false;
	}
	public RobotNetwork(MLMethod method, Robot actor, boolean log) {
		this.method = method; this.actor = actor; this.log = log;
	}
	
	public double scoreRobot() {
		double balBTC = 0.0d;
		double balUSD = 0.0d;
		int totaltrades = 0;
		
		int buysellBTC = 0; //0 = do nothing, +1 = buy, -1 = sell
		double tradeBTC = 0.0d;
		
		List<TemporalPoint> points = FXMLController.dataSet.getPoints();
		
		//i = emulated current data tick(=last point within input window), i+(1,2,6) = future ticks
		int start = Math.max(FXMLController.INPUT_WINDOW_SIZE + FXMLController.PREDICT_WINDOW_SIZE - 1, points.size()-FXMLController.maxTrainHistory);
		for (int i = start; i < points.size(); i++) {
			if (FXMLController.instance == null || FXMLController.instance.runThread == null || FXMLController.instance.runThread.stop) break;
			TemporalPoint point = points.get(i);
			double price = FXMLController.normPrice.deNormalize(point.getData(0)); //original price
			if (price <= 0) continue;
			if (i == start) { //get the balances at the start of our past maxTrainHistory 
				balBTC = point.getData(2);
				balUSD = point.getData(3);
			}
			
			MLData input = FXMLController.dataSet.generateInputNeuralData(i - FXMLController.INPUT_WINDOW_SIZE + 2); //this function goes back one to grab data, actual index start is minus one from this index
			input = new BasicMLData(Arrays.copyOf(input.getData(), Robot.INPUT_NEURONS_ALL));
			input.setData(Robot.INPUT_NEURONS, Math.tanh(balBTC/FXMLController.exptTotalBTC*Math.PI));
			input.setData(Robot.INPUT_NEURONS+1, Math.tanh(balUSD/FXMLController.exptTotalUSD*Math.PI));
			for (int j=0; j < FXMLController.PREDICT_WINDOW_SIZE; j++) input.setData(Robot.INPUT_NEURONS+2+j, point.getData(FXMLController.descPredStart+j)); //currently predicted price for next tick
			
			MLData output = ((BasicNetwork)this.method).compute(input);
			
			
			tradeBTC = Math.round(FXMLController.trade.deNormalize(output.getData(0)-output.getData(1))); //positive=buyBTC,negative=sellBTC

			if (tradeBTC > 0 && balUSD > 0) { //buy BTC
				double balUSDo = balUSD;
				balUSD -= tradeBTC*price;
				if (balUSD < 0) { //buy as much as we have USD for
					balUSD = 0.0d;
					tradeBTC = balUSDo/price;
				}
				balBTC += tradeBTC*FXMLController.fee;
				totaltrades++;
				buysellBTC = +1;
				if (log) System.out.println(String.format("[%5d] Price[%7.2f] BoughtBTC[%9.4f]   BTC[%,12.4f] USD[%,15.2f]", i, price, tradeBTC, balBTC, balUSD));
				
			} else if (tradeBTC < 0 && balBTC > 0) { //sell BTC
				tradeBTC = -tradeBTC;
				double balBTCo = balBTC;
				balBTC -= tradeBTC;
				if (balBTC < 0) { //sell as much BTC as we have
					balBTC = 0.0d;
					tradeBTC = balBTCo;
				}
				balUSD += tradeBTC*price*FXMLController.fee;
				totaltrades++;
				buysellBTC = -1;
				if (log) System.out.println(String.format("[%5d] Price[%7.2f]   SoldBTC[%9.4f]   balBTC[%,12.4f] balUSD[%,15.2f]", i, price, tradeBTC, balBTC, balUSD));
			} else buysellBTC = 0;
			
		}
		if (actor != null) { //set winning values
			actor.balBTC = balBTC;
			actor.balUSD = balUSD;
			actor.totaltrades = totaltrades;
			actor.buysellBTC = buysellBTC;
			actor.tradeBTC = tradeBTC; //last trade ammount for current point/tick
		}
		if (log) System.out.println(String.format("Total Trades [%5d] balUSD[%,15.2f]", totaltrades, balUSD));
		return balUSD; //maximize this
	}
}
