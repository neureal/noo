package com.nootera.noo;

import java.io.IOException;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.btce.v3.BTCEExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.encog.ml.data.temporal.TemporalMLDataSet;
import org.encog.ml.data.temporal.TemporalPoint;

public class GetTicker {
	private static final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(BTCEExchange.class.getName());
	private static final PollingMarketDataService marketDataService = exchange.getPollingMarketDataService();
	
	private static int sequenceNumber = 1;

	public static boolean getNewPoint(TemporalMLDataSet dataSet) throws IOException {
		Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
		
		TemporalPoint point = new TemporalPoint(dataSet.getDescriptions().size());
		point.setSequence(sequenceNumber);
		point.setData(0, FXMLController.normPrice.normalize(ticker.getLast().doubleValue()));
		point.setData(1, FXMLController.normVolume.normalize(ticker.getVolume().doubleValue()));
		point.setData(2, FXMLController.startBalBTC); //starting balance for Actor
		point.setData(3, FXMLController.startBalUSD);
//		point.setData(4, FXMLController.normPrice.normalize(ticker.getHigh().doubleValue()));
//		point.setData(5, FXMLController.normPrice.normalize(ticker.getLow().doubleValue()));
		point.setData(4, FXMLController.normPrice.normalize(ticker.getBid().doubleValue()));
		point.setData(5, FXMLController.normPrice.normalize(ticker.getAsk().doubleValue()));
		point.setData(6, FXMLController.normTimestamp.normalize(ticker.getTimestamp().getTime()/1000-1230940800)); //bitcoin start date
		dataSet.getPoints().add(point);

		sequenceNumber++;
		System.out.println(ticker.toString());
		return true;
	}

}