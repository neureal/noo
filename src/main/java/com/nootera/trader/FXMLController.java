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
	
	private static final int tickVisWindow = 90;
	
	public static TemporalMLDataSet dataSet;
	public static final int INPUT_WINDOW_SIZE = 36; //
	public static final int PREDICT_WINDOW_SIZE = 3; //
	public static final int maxTrainHistory = 120; //number ticks/points backwards to keep(in data) for training
	public static void initDataSet() {
		dataSet = new TemporalMLDataSet(INPUT_WINDOW_SIZE, PREDICT_WINDOW_SIZE); //input points window size, predict points window size
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, true)); //price
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, true, false)); //volume
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current BTC total
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //current USD total
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 1 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 2 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 3 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 4 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 5 tick
		dataSet.addDescription(new TemporalDataDescription(TemporalDataDescription.Type.RAW, false, false)); //predicted price 6 tick
	}
	
	public static final NormalizedField normPrice = new NormalizedField(NormalizationAction.Normalize, "price", 1200.0d, 0.0d, 1.0d, 0.0d); //price
	public static final NormalizedField normVolume = new NormalizedField(NormalizationAction.Normalize, "volume", 100000.0d, 0.0d, 1.0d, 0.0d); //volume
	
	public static final double startBalBTC = 1.0d;
	public static final double startBalUSD = 0.0d;
	public static final NormalizedField trade = new NormalizedField(NormalizationAction.Normalize, "trade", 100.0d, -100.0d, 1.0d, -1.0d); //max BTC trade size
	public static final double fee = 0.99d; //fee per trade
	public static final double exptMaxBTC = 100.0d;
	public static final double exptMaxUSD = 10000.0d;
	
	public static RunThread runThread;
	public class RunThread extends Thread {
		public Boolean stop = false;
		@Override
		public void run() {
			updateProgress(predictProgress, -1.0d);
			chartAddSingle(chartPrediction, 1, 0.0d); //hack to visually show prediction at same tick as predicted
			BtceMarketFile mkt_btce = null;
			try {
				initDataSet();
				Predictor predictor = new Predictor();
				Actor actor = new Actor();
				mkt_btce = new BtceMarketFile();
				
				while (mkt_btce.getNewPoint(dataSet)) {
					if (stop) break;
					
					int newidx = dataSet.getPoints().size();
					if (newidx < INPUT_WINDOW_SIZE + PREDICT_WINDOW_SIZE) continue; //give us at least a input + predict window of data (only at the beginning)
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
					for (int i=0; i < predictions.length; i++) point.setData(4+i, predictions[i]); //add prediction data share output
					//chartAdd(chartPrediction, chartTrading, normPrice.deNormalize(point.getData(0)), normPrice.deNormalize(point.getData(4)), 0.0d, 0.0d);
					
					//****action training
					actor.train();
					point.setData(2, actor.balBTC); //add ongoing balances so that we can move our training window
					point.setData(3, actor.balUSD);
					chartAdd(chartPrediction, chartTrading, normPrice.deNormalize(point.getData(0)), normPrice.deNormalize(point.getData(4)), actor.tradeBTC*0.05d+50d, Math.tanh(actor.balUSD/exptMaxUSD*Math.PI)*100d);
					
					
					//****this is where we would actually execute our trade using actor.buysellBTC and actor.tradeBTC
					
					Thread.sleep(200);
				}
			} catch (InterruptedException ex) {
			} finally {
				if (mkt_btce != null) mkt_btce.close();
				updateProgress(predictProgress, 0.0d);
			}
		}
	}
	
	
	//************
    
    @FXML
    public TextArea outBox;
	public LineChart chartPrediction;
	public LineChart chartTrading;
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
			//runThread.interrupt();
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
	
	public static void chartAddSingle(final LineChart chart, final int seriesIdx, final double value) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				XYChart.Series s = (XYChart.Series)chart.getData().get(seriesIdx);
				s.getData().add(new XYChart.Data(chartx, value));
			}
		});
	}
	
	public static int chartx = 1;
	public static void chartAdd(final LineChart chart1, final LineChart chart2, final double value0, final double value1, final double value2, final double value3) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				XYChart.Series series0 = (XYChart.Series)chart1.getData().get(0);
				series0.getData().add(new XYChart.Data(chartx, value0));
				XYChart.Series series1 = (XYChart.Series)chart1.getData().get(1);
				series1.getData().add(new XYChart.Data(chartx, value1));
				
				XYChart.Series series2 = (XYChart.Series)chart2.getData().get(0);
				series2.getData().add(new XYChart.Data(chartx, value2));
				XYChart.Series series3 = (XYChart.Series)chart2.getData().get(1);
				series3.getData().add(new XYChart.Data(chartx, value3));
				
				chartx++;
				
				NumberAxis xaxis1 = (NumberAxis)chart1.getXAxis();
				xaxis1.setUpperBound(chartx);
				
				NumberAxis xaxis2 = (NumberAxis)chart2.getXAxis();
				xaxis2.setUpperBound(chartx);
				
				if (chartx > tickVisWindow) {
					xaxis1.setLowerBound(xaxis1.getLowerBound() + 1);
					xaxis2.setLowerBound(xaxis2.getLowerBound() + 1);
					series0.getData().remove(0);
					series1.getData().remove(0);
					series2.getData().remove(0);
					series3.getData().remove(0);
				}
				
				//xaxis.invalidateRange(chart.getData());
			}
		});
	}
	
    @Override
    public void initialize(URL url, ResourceBundle rb) {
		redirectSystemStreams(outBox);
		
		//Prediction Chart
		NumberAxis xaxis1 = (NumberAxis)chartPrediction.getXAxis();
		xaxis1.setLowerBound(1);
		xaxis1.setUpperBound(chartx);
		
		XYChart.Series series0 = new XYChart.Series();
        series0.setName("Actual");
        
        XYChart.Series series1 = new XYChart.Series();
        series1.setName("Prediction");
		
        chartPrediction.getData().addAll(series0, series1);
		
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
