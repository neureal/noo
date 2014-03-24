/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.trader;

import java.util.Arrays;
import java.util.List;
import org.encog.ml.MLMethod;
import org.encog.ml.MLRegression;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.temporal.TemporalPoint;
import org.encog.util.arrayutil.NormalizationAction;
import org.encog.util.arrayutil.NormalizedField;

public class ActorNetwork {

	private final MLMethod method;
	private final Actor actor;
	private final boolean log;

	public ActorNetwork(MLMethod method) {
		this.method = method; this.actor = null; this.log = false;
	}
	public ActorNetwork(MLMethod method, Actor actor, boolean log) {
		this.method = method; this.actor = actor; this.log = log;
	}
	
	public double scoreActor() {
		double balBTC = 0;
		double balUSD = 0;
		int totaltrades = 0;
		
		int buysellBTC = 0; //0 = do nothing, +1 = buy, -1 = sell
		double tradeBTC = 0;
		
		List<TemporalPoint> points = FXMLController.dataSet.getPoints();
		//FXMLController.dataSet.sortPoints(); //make sure they are in the right order
		
		//i = emulated current data tick(=last point within input window), i+(1,2,6) = future ticks
		int start = Math.max(FXMLController.INPUT_WINDOW_SIZE + FXMLController.PREDICT_WINDOW_SIZE - 1, points.size()-FXMLController.maxTrainHistory);
		for (int i = start; i < points.size(); i++) {
			TemporalPoint point = points.get(i);
			double price = FXMLController.normPrice.deNormalize(point.getData(0)); //original price
			if (price <= 0) continue;
			if (i == start) { //get the balances at the start of our past maxTrainHistory 
				balBTC = point.getData(2);
				balUSD = point.getData(3);
			}
			
			MLData input = FXMLController.dataSet.generateInputNeuralData(i - FXMLController.INPUT_WINDOW_SIZE + 2); //this function goes back one to grab data, actual index start is minus one from this index
			input = new BasicMLData(Arrays.copyOf(input.getData(), 27));
			input.setData(24, point.getData(4)); //currently predicted price for next tick
			input.setData(25, Math.tanh(balBTC/FXMLController.exptMaxBTC*Math.PI));
			input.setData(26, Math.tanh(balUSD/FXMLController.exptMaxUSD*Math.PI));
			
			MLData output = ((MLRegression)this.method).compute(input);
			
			
			tradeBTC = Math.round(FXMLController.trade.deNormalize(output.getData(0)-output.getData(1))); //positive=buyBTC,negative=sellBTC

			if (tradeBTC > 0 && balUSD > 0) { //buy BTC
				double balUSDo = balUSD;
				balUSD -= tradeBTC*price;
				if (balUSD < 0) { //buy as much as we have USD for
					balUSD = 0;
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
					balBTC = 0;
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
