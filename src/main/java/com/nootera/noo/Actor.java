/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.noo;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.CalculateScore;
import org.encog.ml.MLMethod;
import org.encog.ml.MethodFactory;
import org.encog.ml.genetic.MLMethodGeneticAlgorithm;
import org.encog.ml.train.MLTrain;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.pattern.JordanPattern;

/**
 *
 * @author Wil
 */
public class Actor {
	//winning values after train
	public double balBTC = 0.0d;
	public double balUSD = 0.0d;
	public int totaltrades = 0;
	
	public int buysellBTC = 0; //0 = do nothing, +1 = buy, -1 = sell
	public double tradeBTC = 0.0d;
	
	public static final int INPUT_NEURONS = FXMLController.INPUT_WINDOW_SIZE*FXMLController.descInputCount;
	public static final int INPUT_NEURONS_ALL = INPUT_NEURONS + 2 + FXMLController.PREDICT_WINDOW_SIZE;
	
	private static BasicNetwork createNetwork() {
		JordanPattern pattern = new JordanPattern();
		pattern.setInputNeurons(INPUT_NEURONS_ALL);
		pattern.addHiddenLayer(FXMLController.INPUT_WINDOW_SIZE + FXMLController.descInputCount);
		pattern.setOutputNeurons(2);
		pattern.setActivationFunction(new ActivationSigmoid());
//		pattern.setActivationFunction(new ActivationTANH());
		BasicNetwork network = (BasicNetwork)pattern.generate();
		network.reset();
		return network;
	}
	
//	private static BasicNetwork createNetwork() {
//		FeedForwardPattern pattern = new FeedForwardPattern();
//		pattern.setInputNeurons(INPUT_NEURONS_ALL);
//		pattern.addHiddenLayer(FXMLController.INPUT_WINDOW_SIZE + FXMLController.descInputCount);
//		pattern.setOutputNeurons(2);
//		pattern.setActivationFunction(new ActivationSigmoid()); //0 to +1
////		pattern.setActivationFunction(new ActivationTANH());
//		BasicNetwork network = (BasicNetwork)pattern.generate();
//		network.reset();
//		return network;
//	}
	
	private final MLTrain train;
	public Actor() {
//		train = new NeuralPSO(createNetwork(), new NguyenWidrowRandomizer(), new ActorScore(), FXMLController.actTrainCycles);
		
		train = new MLMethodGeneticAlgorithm(new MethodFactory() {
			@Override
			public MLMethod factor() { return createNetwork(); }
		}, new ActorScore(), FXMLController.actTrainCycles);
	}
	
	private int epoch;
	public void train() {
		epoch = 0;
		for (int i = 0; i < FXMLController.ActTrainEpochs; i++) {
			if (FXMLController.instance == null || FXMLController.instance.runThread == null || FXMLController.instance.runThread.stop) break;
			train.iteration(); //uses multiple threads
			System.out.println(String.format("Actor Epoch[%5d] Score[%15.4f]", epoch, train.getError()));
			epoch++;
			if (train.getError() > FXMLController.exptTotalUSD) break;
		}
		train.finishTraining();
		
		if (FXMLController.instance == null || FXMLController.instance.runThread == null || FXMLController.instance.runThread.stop) return;
		MLMethod method = train.getMethod(); //winning network
		ActorNetwork pilot = new ActorNetwork(method, this, false); //change to true to show how winning network traded
		pilot.scoreActor();
	}
}

class ActorScore implements CalculateScore {
	@Override
	public double calculateScore(MLMethod network) {
		ActorNetwork pilot = new ActorNetwork(network);
		return pilot.scoreActor();
	}
	@Override
	public boolean shouldMinimize() { return false; }
	@Override
	public boolean requireSingleThreaded() { return false; }
}