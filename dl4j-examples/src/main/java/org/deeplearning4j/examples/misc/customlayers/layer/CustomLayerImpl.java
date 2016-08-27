package org.deeplearning4j.examples.misc.customlayers.layer;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

/**
 * Layer (implementation) class for the custom layer example
 *
 * @author Alex Black
 */
public class CustomLayerImpl extends BaseLayer<CustomLayer> { //Generic parameter here: the configuration class type

    public CustomLayerImpl(NeuralNetConfiguration conf) {
        super(conf);
    }


    @Override
    public INDArray preOutput(INDArray x, boolean training) {
        /*
        The preOut method(s) calculate the activations (forward pass), before the activation function is applied.

        Because we aren't doing anything different to a standard dense layer, we can use the existing implementation
        for this. Other network types (RNNs, CNNs etc) will require you to implement this method.

        For custom layers, you may also have to implement methods such as calcL1, calcL2, numParams, etc.
         */

        return super.preOutput(x, training);
    }


    @Override
    public INDArray activate(boolean training) {
        /*
        The activate method is used for doing forward pass. Note that it relies on the pre-output method;
        essentially we are just applying the activation function(s).
        In this particular (contrived) example, we have TWO activation functions - one for the first half of the outputs
        and another for the second half.
         */

        INDArray output = preOutput(training);
        int columns = output.columns();

        INDArray firstHalf = output.get(NDArrayIndex.all(), NDArrayIndex.interval(0, columns / 2));
        INDArray secondHalf = output.get(NDArrayIndex.all(), NDArrayIndex.interval(columns / 2, columns));

        String activation1 = conf.getLayer().getActivationFunction();
        String activation2 = ((CustomLayer) conf.getLayer()).getSecondActivationFunction();

        Nd4j.getExecutioner().exec(Nd4j.getOpFactory().createTransform(activation1, firstHalf));
        Nd4j.getExecutioner().exec(Nd4j.getOpFactory().createTransform(activation2, secondHalf));

        return output;
    }


    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon) {
        INDArray activationDerivative = preOutput(true);
        int columns = activationDerivative.columns();
        INDArray firstHalf = activationDerivative.get(NDArrayIndex.all(), NDArrayIndex.interval(0, columns / 2));
        INDArray secondHalf = activationDerivative.get(NDArrayIndex.all(), NDArrayIndex.interval(columns / 2, columns));

        String activation1 = conf.getLayer().getActivationFunction();
        String activation2 = ((CustomLayer) conf.getLayer()).getSecondActivationFunction();

        Nd4j.getExecutioner().exec(Nd4j.getOpFactory().createTransform(activation1, firstHalf).derivative());
        Nd4j.getExecutioner().exec(Nd4j.getOpFactory().createTransform(activation2, secondHalf).derivative());

        //The remaining code here: just a copy/paste from BaseLayer
        INDArray delta = epsilon.muli(activationDerivative);
        if (maskArray != null) {
            delta.muliColumnVector(maskArray);
        }

        Gradient ret = new DefaultGradient();

        INDArray weightGrad = gradientViews.get(DefaultParamInitializer.WEIGHT_KEY);    //f order
        Nd4j.gemm(input, delta, weightGrad, true, false, 1.0, 0.0);
        INDArray biasGrad = gradientViews.get(DefaultParamInitializer.BIAS_KEY);
        biasGrad.assign(delta.sum(0));  //TODO: do this without the assign

        ret.gradientForVariable().put(DefaultParamInitializer.WEIGHT_KEY, weightGrad);
        ret.gradientForVariable().put(DefaultParamInitializer.BIAS_KEY, biasGrad);

        INDArray epsilonNext = params.get(DefaultParamInitializer.WEIGHT_KEY).mmul(delta.transpose()).transpose();

        return new Pair<>(ret, epsilonNext);
    }

}