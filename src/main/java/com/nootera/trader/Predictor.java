/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.trader;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.engine.network.activation.ActivationTANH;
import org.encog.mathutil.randomize.NguyenWidrowRandomizer;
import org.encog.ml.CalculateScore;
import org.encog.ml.MLMethod;
import org.encog.ml.MLRegression;
import org.encog.ml.MethodFactory;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.genetic.MLMethodGeneticAlgorithm;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.HybridStrategy;
import org.encog.ml.train.strategy.RequiredImprovementStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.TrainingSetScore;
import org.encog.neural.networks.training.propagation.back.Backpropagation;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.neural.networks.training.pso.NeuralPSO;
import org.encog.neural.pattern.FeedForwardPattern;
import org.encog.neural.pattern.JordanPattern;

/**
 *
 * @author Wil
 */
public class Predictor {
	
	//construct an Jordan type network
	private static BasicNetwork createNetwork() {
		JordanPattern pattern = new JordanPattern();
		pattern.setInputNeurons(FXMLController.INPUT_WINDOW_SIZE*2);
		pattern.addHiddenLayer(FXMLController.INPUT_WINDOW_SIZE*5);
		pattern.setOutputNeurons(FXMLController.PREDICT_WINDOW_SIZE);
		pattern.setActivationFunction(new ActivationSigmoid());
//		pattern.setActivationFunction(new ActivationTANH());
		BasicNetwork network = (BasicNetwork)pattern.generate();
		network.reset();
		return network;
	}
	
//	private static BasicNetwork createNetwork() {
//		FeedForwardPattern pattern = new FeedForwardPattern();
//		pattern.setInputNeurons(FXMLController.INPUT_WINDOW_SIZE*2);
//		pattern.addHiddenLayer(FXMLController.INPUT_WINDOW_SIZE + 2);
//		pattern.setOutputNeurons(FXMLController.PREDICT_WINDOW_SIZE);
//		pattern.setActivationFunction(new ActivationSigmoid()); //0 to +1
//		pattern.setActivationFunction(new ActivationTANH());
//		BasicNetwork network = (BasicNetwork)pattern.generate();
//		network.reset();
//		return network;
//	}
	
	private final MLTrain train;
	public Predictor() {
		BasicNetwork network = createNetwork();
		CalculateScore score = new TrainingSetScore(FXMLController.dataSet);
		
		train = new ResilientPropagation(network, FXMLController.dataSet);
		//train.addStrategy(new RequiredImprovementStrategy(0.000000002d, 0.000000005d, 500));
		//train.addStrategy(new RequiredImprovementStrategy(500));
		
		//train = new NeuralPSO(network, FXMLController.dataSet);
		//train = new NeuralPSO(network, new NguyenWidrowRandomizer(), score, 1000);
		final MLTrain trainAlt = new NeuralPSO(network, new NguyenWidrowRandomizer(), score, 1000);
		
//		train = new Backpropagation(network, FXMLController.dataSet, 0.00001d, 0.0d);
//		train.addStrategy(new Greedy());
//		final MLTrain trainAlt = new NeuralSimulatedAnnealing(network, score, 10, 2, 100);
		train.addStrategy(new HybridStrategy(trainAlt));
		
		
		
//		final StopTrainingStrategy stop = new StopTrainingStrategy();
//		train.addStrategy(stop);
		
//		train = new MLMethodGeneticAlgorithm(new MethodFactory() {
//			@Override
//			public MLMethod factor() { return createNetwork(); }
//		}, new ActorScore(), 500);
	}
	
	private int epoch;
	public void train() {
		epoch = 0;
		for (int i = 0; i < 10; i++) {
			if (FXMLController.runThread == null || FXMLController.runThread.stop) break;
			train.iteration();
			System.out.println(String.format("Predictor Epoch[%5d] Error[%23.23f]", epoch, train.getError()));
			if (train.getError() < 0.0000000003d) break; //0.00000000246701472269006
			epoch++;
		}
		train.finishTraining();
	}
	
	public double[] predict(BasicMLData input) {
		MLData output = ((MLRegression)train.getMethod()).compute(input);
		return output.getData();
	}
}
