package com.neureal.noo;

import java.io.IOException;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.bter.BTERExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.encog.ml.data.temporal.TemporalMLDataSet;
import org.encog.ml.data.temporal.TemporalPoint;

public class GetTickerXChange {
	private static final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(BTERExchange.class.getName());
	private static final PollingMarketDataService marketDataService = exchange.getPollingMarketDataService();
	
	private static int sequenceNumber = 1;

	public static boolean getNewPoint(TemporalMLDataSet dataSet) {
		try {
			Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
			
			TemporalPoint point = new TemporalPoint(dataSet.getDescriptions().size());
			point.setSequence(sequenceNumber);
			int idx = 0;
			point.setData(idx++, FXMLController.normPrice.normalize(ticker.getLast().doubleValue()));
			point.setData(idx++, FXMLController.normVolume.normalize(ticker.getVolume().doubleValue()));
			point.setData(idx++, FXMLController.normPrice.normalize(ticker.getHigh().doubleValue()));
			point.setData(idx++, FXMLController.normPrice.normalize(ticker.getLow().doubleValue()));
			point.setData(idx++, FXMLController.normPrice.normalize(ticker.getBid().doubleValue()));
			point.setData(idx++, FXMLController.normPrice.normalize(ticker.getAsk().doubleValue()));
//			point.setData(idx++, FXMLController.normTimestamp.normalize(ticker.getTimestamp().getTime()/1000-1230940800)); //bitcoin start date
			point.setData(idx++, FXMLController.startBalBTC); //starting balance for Robot
			point.setData(idx++, FXMLController.startBalUSD);
			dataSet.getPoints().add(point);
			
			sequenceNumber++;
			System.out.println(ticker.toString());
			return true;
		} catch (ExchangeException | NotAvailableFromExchangeException | NotYetImplementedForExchangeException | IOException ex) {
			return false;
		}
	}

}