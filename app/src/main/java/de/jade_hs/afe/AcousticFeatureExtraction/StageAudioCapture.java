package de.jade_hs.afe.AcousticFeatureExtraction;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;

/**
 * Capture audio using Android's AudioRecorder
 */

public class StageAudioCapture extends Stage {

    final static String LOG = "StageProducer";

    private AudioRecord audioRecord;
    private int buffersize, blocksize_ms, frames;
    private boolean stopRecording = false;


    public StageAudioCapture(HashMap parameter) {
        super(parameter);

        Log.d(LOG, "Setting up audioCapture");

        hasInput = false;

        blocksize_ms = 25;
        frames = blocksize_ms * samplingrate / 100;

        buffersize = AudioRecord.getMinBufferSize(samplingrate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        ) * 4;

        Log.d(LOG, "Buffersize: " + buffersize);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                samplingrate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffersize
        );
    }

    @Override
    protected void process(float[][] temp) {

        int samplesRead, i = 0;
        short[] buffer = new short[buffersize / 2];
        float[][] dataOut = new float[channels][frames];

        audioRecord.startRecording();

        Log.d(LOG, "Started producing");

        Stage.startTime = new Date();

        while (!stopRecording & !Thread.currentThread().isInterrupted()) {

            // read audio data
            samplesRead = audioRecord.read(buffer, 0, buffer.length);

            for (int k = 0; k < samplesRead / 2; k++) {

                // split channels
                dataOut[0][i] = buffer[k * 2];
                dataOut[1][i] = buffer[k * 2 + 1];
                i++;

                if (i >= frames) {

                    // send data to queue, reset dataOut & counter
                    send(dataOut);
                    dataOut = new float[channels][frames];
                    i = 0;

                }
            }
        }

        Log.d(LOG, "Stopped producing");
        audioRecord.stop();
    }

    public void stop() {
        stopRecording = true;
    }

}
