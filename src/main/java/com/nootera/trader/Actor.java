/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.trader;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.CalculateScore;
import org.encog.ml.MLMethod;
import org.encog.ml.MethodFactory;
import org.encog.ml.genetic.MLMethodGeneticAlgorithm;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.RequiredImprovementStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.anneal.NeuralSimulatedAnnealing;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.neural.pattern.FeedForwardPattern;

/**
 *
 * @author Wil
 */
public class Actor {
	//winning values after train
	public double balBTC = 0;
	public double balUSD = 0;
	public int totaltrades = 0;
	
	public int buysellBTC = 0; //0 = do nothing, +1 = buy, -1 = sell
	public double tradeBTC = 0;
	
	private static BasicNetwork createNetwork() {
		FeedForwardPattern pattern = new FeedForwardPattern();
		pattern.setInputNeurons(27);
		pattern.addHiddenLayer(13);
		pattern.setOutputNeurons(2);
		pattern.setActivationFunction(new ActivationSigmoid()); //0 to +1
		BasicNetwork network = (BasicNetwork)pattern.generate();
		network.reset();
		return network;
	}
	
	private final MLTrain train;
	public Actor() {
		train = new MLMethodGeneticAlgorithm(new MethodFactory() {
			@Override
			public MLMethod factor() { return createNetwork(); }
		}, new ActorScore(), 500);
	}
	
	private int epoch;
	public void train() {
		epoch = 0;
		for (int i = 0; i < 60; i++) {
			if (FXMLController.runThread == null || FXMLController.runThread.stop) break;
			train.iteration();
			System.out.println(String.format("Actor Epoch[%5d] Score[%15.4f]", epoch, train.getError()));
			epoch++;
			//if (train.getError() > ActorNetwork.exptMaxUSD) break;
		}
		train.finishTraining();
		
		MLMethod method = train.getMethod();
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