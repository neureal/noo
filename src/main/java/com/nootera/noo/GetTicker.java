/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.noo;

import javax.json.JsonObject;

import org.encog.ml.data.temporal.TemporalMLDataSet;
import org.encog.ml.data.temporal.TemporalPoint;

public class GetTicker {
	
	private static int sequenceNumber = 1;
	private static String last_last = "";
	private static String last_bid = "";
	private static String last_ask = "";
	public static boolean getNewPoint(TemporalMLDataSet dataSet) {
		//try {
		JsonObject jo = null;
		while (true) {
			jo = APIUtil.jsonObject("https://www.bitstamp.net/api/ticker/", "");
			if (
				!last_last.equals(APIUtil.jsonString(jo.get("last")))
				|| !last_bid.equals(APIUtil.jsonString(jo.get("bid")))
				|| !last_ask.equals(APIUtil.jsonString(jo.get("ask")))
			) break;
			try { Thread.sleep(10000); } catch (InterruptedException ex) { }
		}
		last_last = APIUtil.jsonString(jo.get("last"));
		last_bid = APIUtil.jsonString(jo.get("bid"));
		last_ask = APIUtil.jsonString(jo.get("ask"));

		TemporalPoint point = new TemporalPoint(dataSet.getDescriptions().size());
		point.setSequence(sequenceNumber);
		int idx = 0;
		point.setData(idx++, FXMLController.normPrice.normalize(APIUtil.jsonBigDecimal(jo.get("last")).doubleValue()));
		point.setData(idx++, FXMLController.normVolume.normalize(APIUtil.jsonBigDecimal(jo.get("volume")).doubleValue()));
		point.setData(idx++, FXMLController.normPrice.normalize(APIUtil.jsonBigDecimal(jo.get("high")).doubleValue()));
		point.setData(idx++, FXMLController.normPrice.normalize(APIUtil.jsonBigDecimal(jo.get("low")).doubleValue()));
		point.setData(idx++, FXMLController.normPrice.normalize(APIUtil.jsonBigDecimal(jo.get("bid")).doubleValue()));
		point.setData(idx++, FXMLController.normPrice.normalize(APIUtil.jsonBigDecimal(jo.get("ask")).doubleValue()));
		point.setData(idx++, FXMLController.normPrice.normalize(APIUtil.jsonBigDecimal(jo.get("vwap")).doubleValue()));
		point.setData(idx++, FXMLController.normTimestamp.normalize(APIUtil.jsonBigInteger(jo.get("timestamp")).doubleValue()-1230940800)); //bitcoin start date
		point.setData(idx++, FXMLController.startBalBTC); //starting balance for Robot
		point.setData(idx++, FXMLController.startBalUSD);
		dataSet.getPoints().add(point);

		sequenceNumber++;
		//System.out.println(jo.toString());
		return true;
		//} catch (ExchangeException | NotAvailableFromExchangeException | NotYetImplementedForExchangeException | IOException ex) {
		//	return false;
		//}
	}
}