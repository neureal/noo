/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.neureal.noo;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.apache.commons.lang3.ArrayUtils;
import org.encog.Encog;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.basic.BasicMLDataPair;
import org.encog.ml.data.temporal.TemporalDataDescription;
import org.encog.ml.data.temporal.TemporalMLDataSet;
import org.encog.ml.data.temporal.TemporalPoint;
import org.encog.util.arrayutil.NormalizationAction;
import org.encog.util.arrayutil.NormalizedField;
import org.noo4j.core.BtcAccount;
import org.noo4j.core.BtcAddress;
import org.noo4j.core.BtcException;
import org.noo4j.core.BtcInfo;
import org.noo4j.daemon.BtcDaemon;

public class FXMLController implements Initializable {
	public static FXMLController instance;
	
	private static final int tickVisWindow = 90;
	
	public static TemporalMLDataSet dataSet;
	public static final int INPUT_WINDOW_SIZE = 48; //
	public static final int PREDICT_WINDOW_SIZE = 3; //
	public static final int maxTrainHistory = 300; //number ticks/points backwards to keep(in data) for training
	public static final int predictTrainEpochs = 20; //
	public static final int predictTrainCycles = 400; //
	public static final int ActTrainEpochs = 160; //
	public static final int actTrainCycles = 400; //
	public static final long minTickTime = 1000; //
	public static void initDataSet() {
		dataSet = new TemporalMLDataSet(INPUT_WINDOW_SIZE, PREDICT_WINDOW_SIZE); //input points window size, predict points window size
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, true)); //price
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //volume
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //high
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //low
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //bid
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //ask
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //vwap (volume-weighted average price)
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //timestamp
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current BTC total
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current USD total
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 1 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 2 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 3 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 4 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 5 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 6 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 7 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 8 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 9 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 10 tick
	}
	public static final int descInputCount = 8; //number of input values (price,vol,etc)
	public static final int descPredStart = descInputCount+2; //predicted price description start
	
	public static final NormalizedField normPrice = new NormalizedField(NormalizationAction.Normalize, "price", 1200.0d, 0.0d, 1.0d, 0.0d); //price
	public static final NormalizedField normVolume = new NormalizedField(NormalizationAction.Normalize, "volume", 100000.0d, 0.0d, 1.0d, 0.0d); //volume
	public static final NormalizedField normTimestamp = new NormalizedField(NormalizationAction.Normalize, "timestamp", 329709278L, 0L, 1.0d, 0.0d); //seconds timestamp (unix Epoch timestamp)
	
	public static final double startBalBTC = 1.0d;
	public static final double startBalUSD = 0.0d;
	public static final NormalizedField trade = new NormalizedField(NormalizationAction.Normalize, "trade", 100.0d, -100.0d, 1.0d, -1.0d); //max BTC trade size
	public static final double feeLoss = 0.99d; //fee per trade
	public static final double exptTotalBTC = 100.0d;
	public static final double exptTotalUSD = 1000.0d;
	
	public RunThread runThread;
	public class RunThread extends Thread {
		public Boolean stop = false;
		@Override
		public void run() {
			updateProgress(predictProgress, -1.0d);
			//chartAddSingle(chartPrediction, 1, 0, 0.0d); //hack to visually show prediction at same tick as predicted
			GetMarketFile mkt_btce = null;
			try {
				initDataSet();
				Miner predictor = new Miner();
				Robot actor = new Robot();
				mkt_btce = new GetMarketFile();
				
				//while (mkt_btce.getNewPoint(dataSet)) { //emulated new data point/tick
				while (GetTicker.getNewPoint(dataSet)) { //real new data point/tick
					if (stop) break;
					long startT = (new Date()).getTime();
					
					int newidx = dataSet.getPoints().size();
					if (newidx >= INPUT_WINDOW_SIZE + PREDICT_WINDOW_SIZE) { //give us at least a input + predict window of data (only at the beginning)
						TemporalPoint point = dataSet.getPoints().get(newidx - 1);

						//****prediction training
						//generate and add new training pair for prediction training
						final BasicMLData inputT = dataSet.generateInputNeuralData(newidx - (INPUT_WINDOW_SIZE + PREDICT_WINDOW_SIZE) + 1); //it subtracts 1 from index for real index
						dataSet.createPoint(Integer.MAX_VALUE); //hack to fix -1 offset sillyness, only matters if predict window is size 1
						final BasicMLData ideal = dataSet.generateOutputNeuralData(newidx - PREDICT_WINDOW_SIZE + 1); //it subtracts 1 from index for real index
						dataSet.getPoints().remove(newidx); //hack to fix -1 offset sillyness
						final BasicMLDataPair pair = new BasicMLDataPair(inputT, ideal);
						dataSet.getData().add(pair);
						if (dataSet.getData().size() > maxTrainHistory) dataSet.getData().remove(0);
						//train and predict
						predictor.train();
						final BasicMLData inputP = dataSet.generateInputNeuralData(newidx - INPUT_WINDOW_SIZE + 1); //it subtracts one from index anyway
						double[] predictions = predictor.predict(inputP);
						for (int i=0; i < predictions.length; i++) point.setData(descPredStart+i, predictions[i]); //add prediction data share output
						//chartAdd(chartPrediction, chartTrading, normPrice.deNormalize(point.getData(0)), predictions, 0.0d, 0.0d); //comment out when doing both prediction and trading
						
						//for (int i=0; i < predictions.length; i++) {
						//System.out.println(predictions[i]);
						int i = predictions.length-1;
						double pred = normPrice.deNormalize(predictions[i])*100; //*100 to get cents, only send farthest prediction so we dont double them up
						byte[] data = (BigDecimal.valueOf(pred).toBigInteger().toByteArray());
						ArrayUtils.reverse(data); //make little endian
						try {
							noocoind().submitWork(PAPIURL.getText(), BigDecimal.valueOf(1.01), BigInteger.valueOf((long)(i+1)), bytesToHex(data));
						} catch (BtcException ex) {
							ex.printStackTrace();
						}
						//}

						//****action training
						actor.train();
						
						//****this is where we would actually execute our trade using actor.buysellBTC and actor.tradeBTC
						//pull actual balance from API, emulate for now
						double balBTC = actor.balBTC;
						double balUSD = actor.balUSD;
						
						point.setData(descInputCount, balBTC);
						point.setData(descInputCount+1, balUSD);
						
						//****send to display
						setCurrencyBalance(txtBTCtotal, txtUSDtotal, balBTC, balUSD);
						chartAdd(chartPrediction, chartTrading, normPrice.deNormalize(point.getData(0)), predictions, actor.tradeBTC*0.5d+50d, Math.tanh(balUSD/exptTotalUSD*Math.PI)*100d);
					}
					long elapsedT = (new Date()).getTime() - startT;
					if (elapsedT < minTickTime) Thread.sleep(minTickTime - elapsedT);
					else Thread.sleep(200);
				}
			} catch (InterruptedException ex) {
			//} catch (IOException ex) {
			} finally {
				if (mkt_btce != null) mkt_btce.close();
				updateProgress(predictProgress, 0.0d);
			}
		}
	}
	
	
	//************ Miner
    @FXML
    public TextArea outBox;
	public LineChart chartPrediction;
	public LineChart chartTrading;
	public ProgressBar predictProgress;
	private Button buttonClear;
	private Button buttonRun;
	public Label txtBTCtotal;
	public Label txtUSDtotal;
	
    @FXML
    private void onButtonRunAction(ActionEvent event) {
		System.out.println("******** Run");
		
		runThread = new RunThread();
		runThread.start();
    }
    @FXML
    private void onButtonClearAction(ActionEvent event) {
		System.out.println("******** Stop");
		if (runThread != null) {
			runThread.stop = true;
			//runThread.interrupt();
		}
    }
	
	public static void setCurrencyBalance(final Label txtBTC, final Label txtUSD, final double BTCbal, final double USDbal) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				txtBTC.setText(String.format("%,15.7f", BTCbal));
				txtUSD.setText(String.format("%,15.2f", USDbal));
			}
		});
	}
	
	public static void updateProgress(final ProgressBar bar, final double value) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				bar.setProgress(value);
			}
		});
	}
	
	public static void chartAddSingle(final LineChart chart, final int seriesIdx, final int xpos, final double value) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				XYChart.Series s = (XYChart.Series)chart.getData().get(seriesIdx);
				s.getData().add(new XYChart.Data(xpos, value));
			}
		});
	}
	
	public static int chartx = 1;
	public static void chartAdd(final LineChart miner, final LineChart robot, final double actual, final double[] predictions, final double trade, final double bal) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				XYChart.Series series12 = (XYChart.Series)miner.getData().get(1);
				for (int i=0; i < predictions.length; i++) series12.getData().add(new XYChart.Data(chartx+1+i, normPrice.deNormalize(predictions[i]))); //hack to visually show prediction at same tick as predicted
				XYChart.Series series11 = (XYChart.Series)miner.getData().get(0);
				series11.getData().add(new XYChart.Data(chartx, actual));
				
				XYChart.Series series21 = (XYChart.Series)robot.getData().get(0);
				series21.getData().add(new XYChart.Data(chartx, trade));
				XYChart.Series series22 = (XYChart.Series)robot.getData().get(1);
				series22.getData().add(new XYChart.Data(chartx, bal));
				
				chartx++;
				
				NumberAxis xaxis1 = (NumberAxis)miner.getXAxis();
				xaxis1.setUpperBound(chartx+PREDICT_WINDOW_SIZE);
				
				NumberAxis xaxis2 = (NumberAxis)robot.getXAxis();
				xaxis2.setUpperBound(chartx);
				
				if (chartx > tickVisWindow) {
					xaxis1.setLowerBound(xaxis1.getLowerBound() + 1);
					xaxis2.setLowerBound(xaxis2.getLowerBound() + 1);
					series11.getData().remove(0);
					series12.getData().remove(0);
					series21.getData().remove(0);
					series22.getData().remove(0);
				}
				
				//xaxis.invalidateRange(chart.getData());
			}
		});
	}
	
	
	//************ Wallet
	private BtcDaemon noocoind = null;
    public BtcDaemon noocoind() {
		if (noocoind == null) {
			try {
				noocoind = new BtcDaemon(new URL("http://127.0.0.1:41811"), "noocoinrpc", "sskvik3290f87uvkk2sovllshj390gf876fdSGkza1");
			} catch (MalformedURLException ex) {
				System.out.println(ex.toString());
			}
		}
		return noocoind;
	}
	
    @FXML
	public TextField walletReceiveAddress;
	public Label walletSpendableBalance;
	public Label wallet0ConfirmBalance;
	public Label walletCoinage;
	public Label walletNewMint;
	public Label walletStake;
	public Label walletMoneySupply;
	
	public TextField walletSendAddress;
	public TextField walletSendAmount;
	public Button walletButtonSend;
	
	public TextField PAPICoinage;
	public TextField PAPITick;
	public TextField PAPIURL;
	
	
    @FXML
    private void onWalletButtonSend(ActionEvent event) {
		System.out.println("onWalletButtonSend");
		updateWallet();
		
		try {
			BtcAddress toAddr = noocoind().validateAddress(walletSendAddress.getText());
			if (toAddr.isValid()) {
				String tx = noocoind().sendToAddress(toAddr.getAddress(), NumberFormat.getNumberInstance().parse(walletSendAmount.getText()).doubleValue());
				if (tx != null && !"".equals(tx)) walletSendAddress.setPromptText("SUCCESS!");
			} else walletSendAddress.setPromptText("Invalid Address");
		} catch (BtcException ex) {
			noocoind = null;
			walletSendAddress.setPromptText(ex.getMessage());
		} catch (ParseException ex) {
			System.out.println(ex.toString());
		}
		walletSendAddress.setText("");
		walletSendAmount.setText("");
    }
	
    @FXML
    private void onWalletUpdate(ActionEvent event) {
		System.out.println("onWalletUpdate");
		updateWallet();
    }
    @FXML
    private void onWalletClosed(Event event) {
		System.out.println("onWalletClosed");
		if (noocoind == null) updateWallet();
    }
	
	
    private void updateWallet() {
		try {
			String address = noocoind().getAccountAddress("");
			walletReceiveAddress.setText(address);
			
			BtcInfo info = noocoind().getInformation();
			walletSpendableBalance.setText(String.format("%,18.9f", info.getBalance()));
			if (info.getUnconfirmed().compareTo(BigDecimal.ZERO) > 0)
			wallet0ConfirmBalance.setText(String.format("Recieving %,1.9f ИOO", info.getUnconfirmed()));
			else wallet0ConfirmBalance.setText("");
			
			walletCoinage.setText(String.format("%0,20.9f ИOO/days", info.getCoinage()));
			walletNewMint.setText(String.format("%0,20.9f ИOO", info.getNewMint()));
			walletStake.setText(String.format("%0,20.9f ИOO", info.getStake()));
			walletMoneySupply.setText(String.format("%0,20.9f ИOO", info.getMoneySupply()));
			
		} catch (BtcException ex) {
			noocoind = null;
		}
    }
	
	
	//************ test1
    @FXML
	public TextArea testTextArea;
	
    @FXML
    private void onButtonSendPAPIAction(ActionEvent event) {
		try {
			//BigInteger test = BigInteger.valueOf(4294967296L);
			//long test2 = test.longValue();
			double PAPICoinageD = 0.01;
			long PAPITickL = 1L;
			try {
				PAPICoinageD = NumberFormat.getNumberInstance().parse(PAPICoinage.getText()).doubleValue();
				PAPITickL = NumberFormat.getNumberInstance().parse(PAPITick.getText()).longValue();
			} catch (ParseException ex) {
				System.out.println(ex.toString());
			}
			testTextArea.appendText(String.format("PAPI\t\t[%s]\r\n", noocoind().submitVote(PAPIURL.getText(), BigDecimal.valueOf(PAPICoinageD), BigInteger.valueOf(PAPITickL))));
		} catch (BtcException ex) {
			noocoind = null;
		}
		
	}
    @FXML
    private void onButtonSendMPEAction(ActionEvent event) {
//		int i = 2;
//		double pred = 0.2234;
//		pred = normPrice.deNormalize(pred);
//		System.out.println(pred);
//		byte[] data = (BigDecimal.valueOf(pred).toBigInteger().toByteArray());
//		ArrayUtils.reverse(data); //make little endian
//		try {
//			noocoind().submitWork(PAPIURL.getText(), BigDecimal.valueOf(1.01), BigInteger.valueOf((long)i), bytesToHex(data));
//		} catch (BtcException ex) {
//			ex.printStackTrace();
//		}
		
		try {
			long epoctime = (new Date()).getTime()/1000L; //get seconds
			epoctime = Math.floorDiv(epoctime, 30L); //predict that the next tick (change in data) will be this data (each tick happens every 30 seconds)
			byte[] data = (BigInteger.valueOf(epoctime).toByteArray());
			ArrayUtils.reverse(data); //make little endian
			testTextArea.appendText(String.format("MPE\t\t[%s]\r\n", noocoind().submitWork(PAPIURL.getText(), BigDecimal.valueOf(1.01), BigInteger.valueOf(1L), bytesToHex(data))));
		} catch (BtcException ex) {
			noocoind = null;
		}
		
	}
    @FXML
    private void onButtonSendNooTESTAction(ActionEvent event) {
		try {
			testTextArea.appendText(String.format("NooTEST\t\t\t[%,16.8f]\r\n", noocoind().getCoinage()));
		} catch (BtcException ex) {
			noocoind = null;
		}
		
	}
	
    @FXML
    private void onButtonTestAction(ActionEvent event) {
		try {
			
			//String url = "https://btc-e.com/api/2/btc_usd/ticker";
			
//			JsonObject jo = APIUtil.jsonObject("https://www.bitstamp.net/api/ticker/", "");
//			//JsonValue jv = APIUtil.jsonValue(APIUtil.jsonHttpPost("https://www.bitstamp.net/api/ticker/", ""));
//			//JsonValue jo = APIUtil.jsonObject(jv).get("last");
//			BigDecimal x = APIUtil.jsonBigDecimal(jo.get("last"));
//			//JsonValue x = jo.get("last");
//			
//			testTextArea.appendText(x.toString()+"\r\n");
			
//			testTextArea.appendText(APIUtil.jsonHttpPost("https://www.bitstamp.net/api/ticker/", ""));
			
			
			
			
			Map<String, BtcAccount> accounts = noocoind().listAccounts();
			for (Map.Entry<String, BtcAccount> entry : accounts.entrySet()) {
				//BtcAccount ac = entry.getValue();
				//String addr = ac.getAccount();
				//BigDecimal ammt = ac.getAmount();
				//long conf = ac.getConfirmations();
				//testTextArea.appendText(String.format("account\t\tname[%s]\t\tvalue[%,16.8f]\r\n", entry.getKey(), entry.getValue().getAmount()));
			}
			
			String acctName = "";
			List<String> addresses = noocoind().getAddressesByAccount(acctName);
			for (String entry : addresses) {
				testTextArea.appendText(String.format("account[%s]\t\taddress [%s]\r\n", acctName, entry));
			}
			testTextArea.appendText(String.format("account[%s]\t\taddress [%s] main recieve\r\n", acctName, noocoind().getAccountAddress(acctName)));
			
			testTextArea.appendText("\r\n");
			
			String toAddrS = "oBmDEWsBp3NFBSJxEuufDdGyn3kbh2sFNp";
			BtcAddress toAddr = noocoind().validateAddress(toAddrS);
			testTextArea.appendText(String.format("validateAddress\t\t[%s]\r\n", toAddr.isValid()));
			
//			testTextArea.appendText(String.format("sendToAddress\t\t[%s]\r\n", noocoind().sendToAddress(toAddrS, 1111)));
			
//			walletpassphrase
//			noocoind().walletPassphrase("GBxDyFeDMYEHucz6XFRpXDDB2woCU4wi96KD9widEmsj");
//			walletlock
//			noocoind().walletLock();
			
			
			
//			BtcInfo info = noocoind().getInformation();
//			testTextArea.appendText(String.format("getCoinage\t\t\t[%,16.8f]\r\n", info.getCoinage())); //current coinage available to spend
//			testTextArea.appendText(String.format("getBalance\t\t\t[%,16.8f]\r\n", info.getBalance())); //real balance available to spend
//			testTextArea.appendText(String.format("getUnconfirmed\t\t[%,16.8f]\r\n", info.getUnconfirmed())); //amount recieved that still has 0 confirmations
//			testTextArea.appendText(String.format("getNewMint\t\t[%,16.8f]\r\n", info.getNewMint())); //amount minted that cant be spent
//			testTextArea.appendText(String.format("getStake\t\t\t[%,16.8f]\r\n", info.getStake())); //amount set aside for staking
//			testTextArea.appendText(String.format("getMoneySupply\t\t[%,16.8f]\r\n", info.getMoneySupply())); //total money supply of whole coin
			
//			BtcMiningInfo info = noocoind().getMiningInformation();
//			testTextArea.appendText(info.getNetworkGhps()+"\r\n");
			
			
//			
//			testTextArea.appendText(String.format("balance *\t\t\t[%,16.8f]\r\n", noocoind().getBalance("*"))); //double, not real
//			testTextArea.appendText(String.format("balance []\t\t\t[%,16.8f]\r\n", noocoind().getBalance())); //the right way
//			testTextArea.appendText(String.format("balance 0\t\t\t[%,16.8f]\r\n", noocoind().getBalance(0))); //"" for account doesn't work
//			testTextArea.appendText(String.format("balance 1\t\t\t[%,16.8f]\r\n", noocoind().getBalance(1)));
//			testTextArea.appendText(String.format("balance 2\t\t\t[%,16.8f]\r\n", noocoind().getBalance(2)));
			
			//daemon.walletPassphrase("GBxDyFeDMYEHucz6XFRpXDDB2woCU4wi96KD9widEmsj");
			//daemon.sendToAddress("mm48fadf1wJVF341ArWmtwZZGV8s34UGWD", BigDecimal.valueOf(0.72));
			//daemon.walletLock();
		} catch (BtcException ex) {
			noocoind = null;
		} finally {
		}
    }
	
	
	//*******************
	@FXML
	private Button closeButton;
	
    @FXML
    public void onCloseButtonAction(ActionEvent event) {
		close();
		Platform.exit();
    }
	
	
	//*******************
	
    @Override
    public void initialize(URL url, ResourceBundle rb) {
		instance = this;
		redirectSystemStreams(outBox);
		
		//Wallet
		//Map<String,String> env = System.getenv();
		//ProcessBuilder builder = new ProcessBuilder("noocoind.exe","-gen=1","-connect=98.202.20.45","-rpcuser=noocoinrpc","-rpcpassword=sskvik3290f87uvkk2sovllshj390gf876fdSGkza1");
		//Process process = builder.start();
		
		//Prediction Chart
		NumberAxis xaxis1 = (NumberAxis)chartPrediction.getXAxis();
		xaxis1.setLowerBound(1);
		xaxis1.setUpperBound(chartx);
		
        XYChart.Series series1 = new XYChart.Series();
        series1.setName("Actual");
		
		XYChart.Series series0 = new XYChart.Series();
        series0.setName("Prediction");
		
        chartPrediction.getData().addAll(series1, series0);
		
		//Trading Chart
		NumberAxis xaxis2 = (NumberAxis)chartTrading.getXAxis();
		xaxis2.setLowerBound(1);
		xaxis2.setUpperBound(chartx);
		
        XYChart.Series series2 = new XYChart.Series();
        series2.setName("Buy/Sell");
		
        XYChart.Series series3 = new XYChart.Series();
        series3.setName("USD");
        
        chartTrading.getData().addAll(series2, series3);
		
		walletSendAmount.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED , validateNumber(19, 999999999.999999999d));
		walletSendAddress.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED , validateAlpha(34));
    }
    public void close() {
		if (runThread != null) {
			runThread.stop = true;
			runThread.interrupt();
		}
		Encog.getInstance().shutdown();
		
		//noocoind().stop(); // will stop noocoind, not required
    }
	

	//redirect System.out to a textbox
	private static void updateTextArea(final TextArea ta, final String text) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				int len = ta.getLength();
				if (len > 30000) ta.setText(ta.getText(len - 20000, len));
				ta.appendText(text);
			}
		});
	}
	public static void redirectSystemStreams(final TextArea ta) {
		OutputStream out = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				updateTextArea(ta, String.valueOf((char) b));
			}
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				updateTextArea(ta, new String(b, off, len));
			}
			@Override
			public void write(byte[] b) throws IOException {
				write(b, 0, b.length);
			}
		};
		System.setOut(new PrintStream(out, true));
		System.setErr(new PrintStream(out, true));
	}
	
	//GUI text entry validation
	public javafx.event.EventHandler<javafx.scene.input.KeyEvent> validateNumber(final int max_length, final double max_value) {
		return new javafx.event.EventHandler<javafx.scene.input.KeyEvent>() {
			@Override
			public void handle(javafx.scene.input.KeyEvent e) {
				TextField txtF = (TextField)e.getSource();
				//TODO add max_value and min_value check
				if (txtF.getText().length() >= max_length) e.consume();
				if (e.getCharacter().matches("[0-9.]")) {
					if (txtF.getText().contains(".") && e.getCharacter().matches("[.]")) e.consume();
					else if (txtF.getText().length() == 0 && e.getCharacter().matches("[.]")) e.consume();
				} else {
					e.consume();
				}
			}
		};
	}
	public javafx.event.EventHandler<javafx.scene.input.KeyEvent> validateLetters(final int max_length) {
		return new javafx.event.EventHandler<javafx.scene.input.KeyEvent>() {
			@Override
			public void handle(javafx.scene.input.KeyEvent e) {
				TextField txtF = (TextField)e.getSource();
				if (txtF.getText().length() >= max_length) e.consume();
				if (!e.getCharacter().matches("[A-Za-z]")) e.consume();
			}
		};
	}
	public javafx.event.EventHandler<javafx.scene.input.KeyEvent> validateAlpha(final int max_length) {
		return new javafx.event.EventHandler<javafx.scene.input.KeyEvent>() {
			@Override
			public void handle(javafx.scene.input.KeyEvent e) {
				TextField txtF = (TextField)e.getSource();
				if (txtF.getText().length() >= max_length) e.consume();
				if (!e.getCharacter().matches("[A-Za-z0-9]")) e.consume();
			}
		};
	}

	
	//**** helpers
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
	
}
