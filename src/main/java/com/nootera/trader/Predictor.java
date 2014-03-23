/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.trader;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.MLMethod;
import org.encog.ml.MLRegression;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.RequiredImprovementStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.propagation.back.Backpropagation;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.neural.networks.training.pso.NeuralPSO;
import org.encog.neural.pattern.JordanPattern;

/**
 *
 * @author Wil
 */
public class Predictor {
	
	//construct an Jordan type network
	private static BasicNetwork createNetwork() {
		JordanPattern pattern = new JordanPattern();
		pattern.setActivationFunction(new ActivationSigmoid());
		pattern.setInputNeurons(24);
		pattern.addHiddenLayer(48);
		pattern.setOutputNeurons(1);
		BasicNetwork network = (BasicNetwork)pattern.generate();
		network.reset();
		return network;
	}
	
	private final MLTrain train;
	public Predictor() {
		train = new ResilientPropagation(createNetwork(), FXMLController.dataSet);
		train.addStrategy(new RequiredImprovementStrategy(500));
	}
	
	private int epoch;
	public void train() {
		epoch = 0;
		for (int i = 0; i < 200; i++) {
			if (FXMLController.runThread == null || FXMLController.runThread.stop) break;
			train.iteration();
			System.out.println(String.format("Predictor Epoch[%5d] Error[%10.8f]", epoch, train.getError()));
			if (train.getError() < 0.0020) break;
			epoch++;
		}
		train.finishTraining();
	}
	
	public double[] predict(BasicMLData input) {
		MLData output = ((MLRegression)train.getMethod()).compute(input);
		return output.getData();
	}
}
