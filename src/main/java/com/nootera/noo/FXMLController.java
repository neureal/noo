/*
 * Copyright Ã‚Â© 2014 BownCo
 * All rights reserved.
 */

package com.nootera.noo;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.event.ActionEvent;
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
import org.btc4j.core.BtcException;
import org.btc4j.core.BtcInfo;
import org.btc4j.daemon.BtcDaemon;
import org.encog.Encog;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.basic.BasicMLDataPair;
import org.encog.ml.data.temporal.TemporalDataDescription;
import org.encog.ml.data.temporal.TemporalMLDataSet;
import org.encog.ml.data.temporal.TemporalPoint;
import org.encog.util.arrayutil.NormalizationAction;
import org.encog.util.arrayutil.NormalizedField;

public class FXMLController implements Initializable {
	public static FXMLController instance;
	public FXMLController() { instance = this; }
	
	private static final int tickVisWindow = 90;
	
	public static TemporalMLDataSet dataSet;
	public static final int INPUT_WINDOW_SIZE = 48; //
	public static final int PREDICT_WINDOW_SIZE = 3; //
	public static final int maxTrainHistory = 300; //number ticks/points backwards to keep(in data) for training
	public static final int predictTrainEpochs = 20; //
	public static final int predictTrainCycles = 400; //
	public static final int ActTrainEpochs = 160; //
	public static final int actTrainCycles = 400; //
	public static final long minTickTime = 300; //
	public static void initDataSet() {
		dataSet = new TemporalMLDataSet(INPUT_WINDOW_SIZE, PREDICT_WINDOW_SIZE); //input points window size, predict points window size
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, true)); //price
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //volume
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current BTC total
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current USD total
//		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //high
//		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //low
//		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //bid
//		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //ask
//		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //timestamp
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
	public static final int descPredStart = 4; //predicted price description start
	public static final int descInputCount = 2; //total number of values per input
	
	public static final NormalizedField normPrice = new NormalizedField(NormalizationAction.Normalize, "price", 1200.0d, 0.0d, 1.0d, 0.0d); //price
	public static final NormalizedField normVolume = new NormalizedField(NormalizationAction.Normalize, "volume", 100000.0d, 0.0d, 1.0d, 0.0d); //volume
	public static final NormalizedField normTimestamp = new NormalizedField(NormalizationAction.Normalize, "timestamp", 329709278L, 0L, 1.0d, 0.0d); //seconds timestamp (unix Epoch timestamp)
	
	public static final double startBalBTC = 1.0d;
	public static final double startBalUSD = 0.0d;
	public static final NormalizedField trade = new NormalizedField(NormalizationAction.Normalize, "trade", 100.0d, -100.0d, 1.0d, -1.0d); //max BTC trade size
	public static final double fee = 0.99d; //fee per trade
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
				
				while (mkt_btce.getNewPoint(dataSet)) {
				//while (GetTicker.getNewPoint(dataSet)) {
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

						//****action training
						actor.train();
						point.setData(2, actor.balBTC); //add ongoing balances so that we can move our training window
						point.setData(3, actor.balUSD);
						setCurrencyBalance(txtBTCtotal, txtUSDtotal, actor.balBTC, actor.balUSD);
						chartAdd(chartPrediction, chartTrading, normPrice.deNormalize(point.getData(0)), predictions, actor.tradeBTC*0.5d+50d, Math.tanh(actor.balUSD/exptTotalUSD*Math.PI)*100d);


						//****this is where we would actually execute our trade using actor.buysellBTC and actor.tradeBTC
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
	public BtcDaemon noocoind;
	
    @FXML
	public TextField walletReceiveAddress;
	public Label walletSpendableBalance;
	public Label wallet0ConfirmBalance;
	public TextField walletSendAddress;
	public TextField walletSendAmount;
	private Button walletButtonSend;
	
    @FXML
    private void onWalletButtonSend(ActionEvent event) {
		
    }
	
	
	//************ test1
    @FXML
	public TextArea testTextArea;
	
    @FXML
    private void onButtonTestAction(ActionEvent event) {
		try {
//			BtcInfo info = noocoind.getInformation();
//			testTextArea.appendText(info.getBalance().toString()+"\r\n");
			
			String address = noocoind.getAccountAddress("");
			testTextArea.appendText(address+"\r\n");
			
//			BigDecimal balance = noocoind.getBalance(1);
//			testTextArea.appendText(balance.toString()+"\r\n");
			
			//daemon.walletPassphrase("GBxDyFeDMYEHucz6XFRpXDDB2woCU4wi96KD9widEmsj");
			//daemon.sendToAddress("mm48fadf1wJVF341ArWmtwZZGV8s34UGWD", BigDecimal.valueOf(0.72));
			//daemon.walletLock();
		} catch (BtcException ex) {
			System.out.println(ex.getMessage());
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
	
    public void close() {
		if (runThread != null) {
			runThread.stop = true;
			runThread.interrupt();
		}
		Encog.getInstance().shutdown();
		
		//noocoind.stop(); // will stop bitcoind, not required
    }
	
    @Override
    public void initialize(URL url, ResourceBundle rb) {
		redirectSystemStreams(outBox);
		
		try {
			//Wallet
			//Map<String,String> env = System.getenv();
			//ProcessBuilder builder = new ProcessBuilder("noocoind.exe","-gen=1","-connect=98.202.20.45","-rpcuser=noocoinrpc","-rpcpassword=sskvik3290f87uvkk2sovllshj390gf876fdSGkza1");
			//Process process = builder.start();
			
			noocoind = new BtcDaemon(new URL("http://127.0.0.1:41801"), "noocoinrpc", "sskvik3290f87uvkk2sovllshj390gf876fdSGkza1");
			
			String address = noocoind.getAccountAddress("");
			walletReceiveAddress.setText(address);

			BigDecimal balance = noocoind.getBalance(1);
			walletSpendableBalance.setText(balance.toString());
				
			
		} catch (BtcException ex) {
			System.out.println(ex.getMessage());
		} catch (MalformedURLException ex) {
			System.out.println(ex.getMessage());
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
		}
		
		//Prediction Chart
		NumberAxis xaxis1 = (NumberAxis)chartPrediction.getXAxis();
		xaxis1.setLowerBound(1);
		xaxis1.setUpperBound(chartx);
		
        XYChart.Series series1 = new XYChart.Series();
        series1.setName("Prediction");
		
		XYChart.Series series0 = new XYChart.Series();
        series0.setName("Actual");
		
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
  
}
