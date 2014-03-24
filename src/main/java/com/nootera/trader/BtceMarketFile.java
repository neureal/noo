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
  
	public boolean getNewPoint(TemporalMLDataSet dataSet) {
		if (csv.next()) {
			int timestamp = csv.getInt(0);
			//if (timestamp >= 1366487996) return false;

			double priceUSD = csv.getDouble(1);
			double volumeBTC = csv.getDouble(2);
			
			TemporalPoint point = new TemporalPoint(dataSet.getDescriptions().size());
			point.setSequence(sequenceNumber);
			point.setData(0, FXMLController.normPrice.normalize(priceUSD));
			point.setData(1, FXMLController.normVolume.normalize(volumeBTC));
			point.setData(2, FXMLController.startBalBTC); //starting balance for Actor
			point.setData(3, FXMLController.startBalUSD);
			dataSet.getPoints().add(point);

			sequenceNumber++;
			return true;
		} else {
			return false;
		}
	}
	
	public void close() {
		csv.close();
	}
}
