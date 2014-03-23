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
import com.xeiam.xchange.bter.BTERExchange;
import com.xeiam.xchange.bter.dto.marketdata.BTERDepth;
import com.xeiam.xchange.bter.dto.marketdata.BTERTicker;
import com.xeiam.xchange.bter.dto.marketdata.BTERTradeHistory;
import com.xeiam.xchange.bter.dto.marketdata.BTERTradeHistory.BTERPublicTrade;
import com.xeiam.xchange.bter.service.polling.BTERPollingMarketDataServiceRaw;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.ExchangeInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BterMarketData {

  public static File download() throws IOException {
	File rawFile = new File(".", "bter_BTC_USD.csv");
	if (rawFile.exists()) {
		System.out.println("Data already downloaded to: " + rawFile.getPath());
	} else {
		System.out.println("Downloading sunspot data to: " + rawFile.getPath());

		Exchange exchange = ExchangeFactory.INSTANCE.createExchange(BTERExchange.class.getName());
		PollingMarketDataService marketDataService = exchange.getPollingMarketDataService();

		generic(marketDataService);
	}
	
	return rawFile;
  }

  private static void generic(PollingMarketDataService marketDataService) throws IOException {

    ExchangeInfo exchangeInfo = marketDataService.getExchangeInfo();
    System.out.println(exchangeInfo);

    Ticker ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
    System.out.println(ticker);

    OrderBook oderBook = marketDataService.getOrderBook(CurrencyPair.BTC_USD);
    System.out.println(oderBook);

    Trades tradeHistory = marketDataService.getTrades(CurrencyPair.BTC_USD);
    System.out.println(tradeHistory);

    List<Trade> trades = tradeHistory.getTrades();
    if (trades.size() > 1) {
      Trade trade = trades.get(trades.size() - 2);
      tradeHistory = marketDataService.getTrades(CurrencyPair.BTC_USD, Long.valueOf(trade.getId()));
      System.out.println(tradeHistory);
    }
  }
}
