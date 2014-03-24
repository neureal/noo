/*
 * Copyright Â© 2014 BownCo
 * All rights reserved.
 */

package com.nootera.trader;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
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
	
	public static TemporalMLDataSet dataSet;
	public static final int INPUT_WINDOW_SIZE = 12;
	public static final int PREDICT_WINDOW_SIZE = 1;
	public static void initDataSet() {
		dataSet = new TemporalMLDataSet(INPUT_WINDOW_SIZE, PREDICT_WINDOW_SIZE); //input points window size, predict points window size
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, true)); //price
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //volume
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current BTC total
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current USD total
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 1 tick
	}
	public static final NormalizedField normPrice = new NormalizedField(NormalizationAction.Normalize, "price", 1200, 0, 1, 0); //price
	public static final NormalizedField normVolume = new NormalizedField(NormalizationAction.Normalize, "volume", 300, 0, 1, 0); //volume
	public static final int maxTrainHistory = 100; //number ticks/points backwards to keep(in data) for training
	
	public static final double startBalBTC = 1;
	public static final double startBalUSD = 0;
	
	public static final NormalizedField trade = new NormalizedField(NormalizationAction.Normalize, "trade", 100, -100, 1, -1); //max BTC trade size
	public static final double fee = 0.99F; //fee per trade
	public static final double exptMaxBTC = 100;
	public static final double exptMaxUSD = 10000;
	
	public static RunThread runThread;
	public class RunThread extends Thread {
		public Boolean stop = false;
		@Override
		public void run() {
			updateProgress(predictProgress, -1.0F);
			try {
				initDataSet();
				Predictor predictor = new Predictor();
				Actor actor = new Actor();
				BtceMarketFile mkt_btce = new BtceMarketFile();
				
				while (mkt_btce.getNewPoint(dataSet)) {
					if (FXMLController.runThread == null || FXMLController.runThread.stop) break;
					
					int newidx = dataSet.getPoints().size();
					if (newidx < INPUT_WINDOW_SIZE + PREDICT_WINDOW_SIZE) continue; //give us at least a input + predict window of data (only at the beginning)
					TemporalPoint point = dataSet.getPoints().get(newidx - 1);
					
					//generate and add new training pair for prediction training
					final BasicMLData inputT = dataSet.generateInputNeuralData(newidx - (INPUT_WINDOW_SIZE + PREDICT_WINDOW_SIZE) + 1); //it subtracts 1 from index for real index
					
					dataSet.createPoint(Integer.MAX_VALUE); //hack to fix -1 offset sillyness, only matters if predict window is size 1
					final BasicMLData ideal = dataSet.generateOutputNeuralData(newidx - PREDICT_WINDOW_SIZE + 1); //it subtracts 1 from index for real index
					dataSet.getPoints().remove(newidx);
					
					final BasicMLDataPair pair = new BasicMLDataPair(inputT, ideal);
					dataSet.getData().add(pair);
					if (dataSet.getData().size() > maxTrainHistory) dataSet.getData().remove(0);
					predictor.train();
					
					final BasicMLData inputP = dataSet.generateInputNeuralData(newidx - INPUT_WINDOW_SIZE + 1); //it subtracts one from index anyway
					double[] predictions = predictor.predict(inputP);
					for (int i=0; i < predictions.length; i++) point.setData(4+i, predictions[i]); //add prediction data share output
					
					actor.train();
					point.setData(2, actor.balBTC); point.setData(3, actor.balUSD); //add ongoing balances so that we can move our training window
					chartAdd(lineChart, normPrice.deNormalize(point.getData(0)), normPrice.deNormalize(point.getData(4)), actor.tradeBTC*.05+50, Math.tanh(actor.balUSD/exptMaxUSD*Math.PI)*100);
					
					//this is where we would actually execute our trade using actor.buysellBTC and actor.tradeBTC
					
					try {
						Thread.sleep(200);
					} catch (InterruptedException ex) {
						break;
					}
				}
				mkt_btce.close();
				
			} finally {
				updateProgress(predictProgress, 0.0F);
			}
		}
	}
	
	
	//************
    
    @FXML
    public TextArea outBox;
	public LineChart lineChart;
	public ProgressBar predictProgress;
	
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
			runThread.interrupt();
		}
    }
	
    @FXML
    private void onCloseButtonAction(ActionEvent event) {
		if (runThread != null) {
			runThread.stop = true;
			runThread.interrupt();
		}
		Encog.getInstance().shutdown();
		Platform.exit();
    }
	
	public static void updateProgress(final ProgressBar bar, final double value) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				bar.setProgress(value);
			}
		});
	}
	
	public static int chartx = 1;
	public static void chartAdd(final LineChart chart, final double series1, final double series2, final double series3, final double series4) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				XYChart.Series s1 = (XYChart.Series)chart.getData().get(0);
				s1.getData().add(new XYChart.Data(chartx, series1));
				XYChart.Series s2 = (XYChart.Series)chart.getData().get(1);
				s2.getData().add(new XYChart.Data(chartx, series2));
				XYChart.Series s3 = (XYChart.Series)chart.getData().get(2);
				s3.getData().add(new XYChart.Data(chartx, series3));
				XYChart.Series s4 = (XYChart.Series)chart.getData().get(3);
				s4.getData().add(new XYChart.Data(chartx, series4));
				chartx++;
				
				NumberAxis xaxis = (NumberAxis)chart.getXAxis();
				xaxis.setUpperBound(chartx);
				
				if (chartx > 50) {
					xaxis.setLowerBound(xaxis.getLowerBound() + 1);
					s1.getData().remove(0);
					s2.getData().remove(0);
					s3.getData().remove(0);
					s4.getData().remove(0);
				}
				
				//xaxis.invalidateRange(chart.getData());
			}
		});
	}
	
    @Override
    public void initialize(URL url, ResourceBundle rb) {
		redirectSystemStreams(outBox);
		
		NumberAxis xaxis = (NumberAxis)lineChart.getXAxis();
		xaxis.setLowerBound(1);
		xaxis.setUpperBound(chartx);
		
		XYChart.Series series1 = new XYChart.Series();
        series1.setName("Actual");
        
        XYChart.Series series2 = new XYChart.Series();
        series2.setName("Prediction");
        
        XYChart.Series series3 = new XYChart.Series();
        series3.setName("Buy/Sell");
		
        XYChart.Series series4 = new XYChart.Series();
        series4.setName("USD");
        
        lineChart.getData().addAll(series1, series2, series3, series4);
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
