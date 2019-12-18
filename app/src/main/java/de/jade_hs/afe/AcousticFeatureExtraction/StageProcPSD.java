package de.jade_hs.afe.AcousticFeatureExtraction;

import org.jtransforms.fft.FloatFFT_1D;

import java.util.HashMap;

/**
 * Feature extraction: Auto- and cross correlation
 */

public class StageProcPSD extends Stage {

    final static String LOG = "StageProcPSD";

    CPSD cpsd;

    public StageProcPSD(HashMap parameter) {
        super(parameter);
        cpsd = new CPSD(blockSize);
    }


    @Override
    protected void process(float[][] buffer) {
        cpsd.calculate(buffer);
    }


    private class CPSD {

        float alpha;
        float[] window, Xx, Xy;
        float[][] data, dataConj, P, Ptemp;
        int nfft, samples, block = 0;
        FloatFFT_1D fft;

        CPSD(int samples) {

            this.samples = samples;
            nfft = nextpow2(samples);
            window = hann(samples, nfft);
            fft = new FloatFFT_1D(nfft);
            alpha = (float) Math.exp(-(blockSize - hopSize) / (samplingrate * 0.125));

        }

        void calculate(float[][] input) {

            data = dataConj = new float[channels][nfft * 2];
            P = Ptemp = new float[3][nfft * 2];

            for (int iChannel = 0; iChannel < data.length; iChannel++) {

                // window
                for (int i = 0; i < samples; i++) {
                    data[iChannel][i] = input[iChannel][i] * window[i];
                }

                // FFT
                fft.realForwardFull(data[iChannel]);

                // complex conjugate
                for (int iSample = 3; iSample < samples; iSample += 2) {
                    dataConj[iChannel][iSample] = data[iChannel][iSample];
                }
            }

            // correlation
            for (int i = 0; i < 2 * nfft - 1; i += 2) {
                P[0][i] = data[0][i] * dataConj[1][i] - data[0][i + 1] * dataConj[1][i + 1];
                P[0][i + 1] = data[0][i] * dataConj[1][i + 1] - data[0][i + 1] * dataConj[1][i];

                P[1][i] = data[0][i] * dataConj[0][i] - data[0][i + 1] * dataConj[0][i + 1];
                P[1][i + 1] = data[0][i] * dataConj[0][i + 1] - data[0][i + 1] * dataConj[0][i];

                P[2][i] = data[1][i] * dataConj[1][i] - data[1][i + 1] * dataConj[1][i + 1];
                P[2][i + 1] = data[1][i] * dataConj[1][i + 1] - data[1][i + 1] * dataConj[1][i];
            }


            // recursive averaging & store data for next average
            for (int k = 0; k < 3; k++) {
                for (int i = 0; i < nfft * 2; i++) {
                    P[k][i] = Ptemp[k][i] = alpha * Ptemp[k][i] + (1 - alpha) * P[k][i];
                }
            }

            // count blocks
            block++;

            // write a block every 125 ms, i.e. every 10th block (for blocksize of 25ms)
            if (block >= 10) {

                float[][] dataOut = new float[3][];
                dataOut[0] = new float[nfft]; // complex spectrum (cross-correlation)
                dataOut[1] = new float[nfft / 2 + 1]; // real spectrum (auto-correlation)
                dataOut[2] = new float[nfft / 2 + 1]; // real spectrum (auto-correlation)

                // copy one-sided spectrum for cross-correlation and scale
                System.arraycopy(P[0], 0, dataOut[0], 0, nfft);
                dataOut[0][0] = P[0][0] / samplingrate;
                dataOut[0][1] = P[0][1] / samplingrate;
                dataOut[0][nfft - 2] = P[0][nfft - 2] / samplingrate;
                dataOut[0][nfft - 1] = P[0][nfft - 1] / samplingrate;
                for (int i = 2; i < nfft - 2; i++) {
                    dataOut[0][i] = P[0][2 * i] / 2 * samplingrate;
                }


                // copy one sided spectrum for auto correlation, scale, omit imaginary parts
                for (int k = 1; k < 3; k++) {
                    dataOut[k][0] = P[k][0] / samplingrate;
                    dataOut[k][nfft / 2] = P[k][nfft] / samplingrate;
                    for (int i = 1; i < nfft / 2; i++) {
                        dataOut[k][i] = P[k][2 * i] / 2 * samplingrate;
                    }
                }

                send(dataOut);
                block = 0;

            }

        }


        private int nextpow2(int x) {

            return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));

        }


        private float[] hann(int samples, int nfft) {

            float[] window = new float[samples];
            float norm = 0;

            // calculate window & normalisation constant
            for (int i = 0; i < samples; i++) {
                window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * (float) i / samples));
                norm += window[i] * window[i];
            }
            norm *= (float) samples / nfft;

            // normalise
            for (int i = 0; i < samples; i++) {
                window[i] /= norm;
            }

            return window;

        }

    }

}