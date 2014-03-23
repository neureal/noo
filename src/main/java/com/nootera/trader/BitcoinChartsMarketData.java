/**
 * Copyright (C) 2012 - 2014 Xeiam LLC http://xeiam.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.nootera.trader;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.bitcoincharts.BitcoinChartsExchange;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Demo requesting polling Ticker at BitcoinCharts
 * 
 * @author timmolter
 */
public class BitcoinChartsMarketData {

  public static File download() throws IOException {
	File rawFile = new File(".", "bter_BTC_USD.csv");
	if (rawFile.exists()) {
		System.out.println("Data already downloaded to: " + rawFile.getPath());
	} else {
		System.out.println("Downloading sunspot data to: " + rawFile.getPath());

		// Use the factory to get BitcoinCharts exchange API using default settings
		Exchange bitcoinChartsExchange = ExchangeFactory.INSTANCE.createExchange(BitcoinChartsExchange.class.getName());

		// Interested in the public polling market data feed (no authentication)
		PollingMarketDataService marketDataService = bitcoinChartsExchange.getPollingMarketDataService();

		generic(marketDataService);
	}
	
	return rawFile;
  }
  public static void generic(PollingMarketDataService marketDataService) throws IOException {

    // Get the latest ticker data showing BTC/bitstampUSD
    CurrencyPair currencyPair = new CurrencyPair(Currencies.BTC, "bitstampUSD");
    Trades tradeHistory = marketDataService.getTrades(currencyPair);

	
    List<Trade> trades = tradeHistory.getTrades();
    if (trades.size() > 1) {
      Trade trade = trades.get(trades.size() - 2);
      tradeHistory = marketDataService.getTrades(currencyPair, Long.valueOf(trade.getId()));
      System.out.println(tradeHistory);
    }

  }
}
