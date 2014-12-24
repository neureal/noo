/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.neureal.noo;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.CalculateScore;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.HybridStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.TrainingSetScore;
import org.encog.neural.networks.training.anneal.NeuralSimulatedAnnealing;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.neural.pattern.JordanPattern;

/**
 *
 * @author Wil
 */
public class Miner {
	
	private static BasicNetwork createNetwork() {
		JordanPattern pattern = new JordanPattern();
		pattern.setInputNeurons(FXMLController.INPUT_WINDOW_SIZE*FXMLController.descInputCount);
		pattern.addHiddenLayer(FXMLController.INPUT_WINDOW_SIZE*FXMLController.descInputCount*3);
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
	private BasicNetwork network;
	public Miner() {
		network = createNetwork();
		CalculateScore score = new TrainingSetScore(FXMLController.dataSet);
		
		train = new ResilientPropagation(network, FXMLController.dataSet);
		//train.addStrategy(new RequiredImprovementStrategy(0.000000002d, 0.000000005d, 500));
		//train.addStrategy(new RequiredImprovementStrategy(500));
		
		//train = new NeuralPSO(network, FXMLController.dataSet);
		//train = new NeuralPSO(network, new NguyenWidrowRandomizer(), score, FXMLController.predictTrainCycles);
		//final MLTrain trainAlt = new NeuralPSO(network, new NguyenWidrowRandomizer(), score, FXMLController.predictTrainCycles);
		
//		train = new MLMethodGeneticAlgorithm(new MethodFactory() {
//			@Override
//			public MLMethod factor() { return createNetwork(); }
//		}, score, FXMLController.predictTrainCycles);
		
//		train = new Backpropagation(network, FXMLController.dataSet, 0.00001d, 0.0d);
//		train.addStrategy(new Greedy());
		
		final MLTrain trainAlt = new NeuralSimulatedAnnealing(network, score, 10, 2, FXMLController.predictTrainCycles);
		train.addStrategy(new HybridStrategy(trainAlt));
		
		
//		final StopTrainingStrategy stop = new StopTrainingStrategy();
//		train.addStrategy(stop);
	}
	
	private int epoch;
	public void train() {
		epoch = 0;
		for (int i = 0; i < FXMLController.predictTrainEpochs; i++) {
			if (FXMLController.instance == null || FXMLController.instance.runThread == null || FXMLController.instance.runThread.stop) break;
			train.iteration();
			//double error = train.getError();
			double error = network.calculateError(FXMLController.dataSet);
			System.out.println(String.format("Predictor Epoch[%5d] Error[%23.23f]", epoch, error));
			if (error < 0.00000003d) break; //0.00000000246701472269006
			epoch++;
		}
		train.finishTraining();
	}
	
	public double[] predict(BasicMLData input) {
		//System.out.println(String.format("***train.getError [%23.23f]", train.getError()));
		//System.out.println(String.format("***network.calculateError [%23.23f]", network.calculateError(FXMLController.dataSet)));
		
		//use for PSO
//		MLData output = ((MLRegression)train.getMethod()).compute(input);
////		double test_error = train.getError();
//		double[] test = output.getData();

//		MLData output1 = ((BasicNetwork)train.getMethod()).compute(input);
//		double test1_error = ((BasicNetwork)train.getMethod()).calculateError(FXMLController.dataSet);
//		double[] test1 = output1.getData();
//
//		
//		MLData output2 = network.compute(input);
//		double test2_error = network.calculateError(FXMLController.dataSet);
//		double[] test2 = output2.getData();
//		
////		BasicNetwork network = ((BasicNetwork)train.getMethod());
//		
		//use for ResilientPropagation + NeuralSimulatedAnnealing
		BasicNetwork clone = (BasicNetwork)network.clone();
		clone.clearContext();
		MLData output3 = clone.compute(input);
		//double test3_error = clone.calculateError(FXMLController.dataSet);
		double[] test3 = output3.getData();
		
		return test3;
	}
}
