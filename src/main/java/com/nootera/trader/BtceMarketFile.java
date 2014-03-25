/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.trader;

import java.io.File;
import org.encog.ml.data.temporal.TemporalMLDataSet;
import org.encog.ml.data.temporal.TemporalPoint;
import org.encog.util.csv.ReadCSV;

public class BtceMarketFile {
	private final File history;
	private final ReadCSV csv;
	public int sequenceNumber;
	
	public BtceMarketFile() {
		sequenceNumber = 1;
		history = new File(".", "btceUSD.csv");
		csv = new ReadCSV(history.toString(), false, ',');
	}
	
	private static final int TIME_RESOLUTION = 60*60*24; //1 day
	public boolean getNewPoint(TemporalMLDataSet dataSet) {
		double priceT = 0.0d;
		double volumeT = 0.0d;
		int cnt = 0;
		int last_timestamp = 0;
		while (csv.next()) {
			if (FXMLController.runThread == null || FXMLController.runThread.stop) break;
			int timestamp = csv.getInt(0);
			//if (timestamp >= 1366487996) return false;
			if (cnt == 0) last_timestamp = timestamp;
			
			double priceUSD = csv.getDouble(1);
			priceT += priceUSD;
			double volumeBTC = csv.getDouble(2);
			volumeT += volumeBTC;
			cnt++;
			
			if (timestamp <= last_timestamp+TIME_RESOLUTION) continue;
			
			TemporalPoint point = new TemporalPoint(dataSet.getDescriptions().size());
			point.setSequence(sequenceNumber);
			double avg = priceT/(double)cnt;
			point.setData(0, FXMLController.normPrice.normalize(avg));
			point.setData(1, FXMLController.normVolume.normalize(volumeT));
			point.setData(2, FXMLController.startBalBTC); //starting balance for Actor
			point.setData(3, FXMLController.startBalUSD);
			dataSet.getPoints().add(point);

			sequenceNumber++;
			return true;
		}
		return false;
	}
	
	public void close() {
		csv.close();
	}
}
