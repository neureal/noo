/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.noo;

import java.io.File;
import javafx.application.Application;
import org.encog.ml.data.temporal.TemporalMLDataSet;
import org.encog.ml.data.temporal.TemporalPoint;
import org.encog.util.csv.ReadCSV;

public class GetMarketFile {
	private final File history;
	private final ReadCSV csv;
	public int sequenceNumber;
	
	public GetMarketFile() {
		sequenceNumber = 1;
		history = new File(".", "btceUSD_small.csv");
		csv = new ReadCSV(history.toString(), false, ',');
	}
	
	private static final int TIME_RESOLUTION = 60*60; //1 day
	public boolean getNewPoint(TemporalMLDataSet dataSet) {
		double priceT = 0.0d;
		double volumeT = 0.0d;
		int cnt = 0;
		long last_timestamp = 0;
		while (csv.next()) {
			if (FXMLController.instance == null || FXMLController.instance.runThread == null || FXMLController.instance.runThread.stop) break;
			long timestamp = Math.round(csv.getDouble(0));
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
			int idx = 0;
			point.setData(idx++, FXMLController.normPrice.normalize(avg));
			point.setData(idx++, FXMLController.normVolume.normalize(volumeT));
			//point.setData(idx++, FXMLController.normTimestamp.normalize(timestamp-1230940800)); //bitcoin start date
			point.setData(idx++, FXMLController.startBalBTC); //starting balance for Robot
			point.setData(idx++, FXMLController.startBalUSD);
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
