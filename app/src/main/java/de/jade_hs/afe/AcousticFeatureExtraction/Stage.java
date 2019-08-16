package de.jade_hs.afe.AcousticFeatureExtraction;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class to implement producers (output), consumers (input) and conducers (in- and output).
 * Data is transferred using queues.
 */

abstract class Stage extends TreeSet {

    final static String LOG = "Stage";

    static Date startTime;
    static int samplingrate;
    static int channels;

    boolean hasInput = true; // default mode, use false to bypass receive() & rebuffer()

    private Thread thread;
    private LinkedBlockingQueue<float[][]> inQueue;
    private Set<LinkedBlockingQueue> outQueue = new HashSet<>();

    ArrayList<Stage> consumerSet = new ArrayList<>();

    // params to set via constructor
    final int id, blockSize, hopSize;

    public Stage(HashMap parameter) {

        id = Integer.parseInt((String) parameter.get("id"));

        Log.d("Stage", " Constructing stage ID " + id + ".");

        if (parameter.get("blocksize") == null)
            blockSize = 400;
        else
            blockSize = Integer.parseInt((String) parameter.get("blocksize"));

        if (parameter.get("hopsize") == null)
            hopSize = blockSize;
        else
            hopSize = Integer.parseInt((String) parameter.get("hopsize"));
    }


    void start() {

        Log.d("Stage", " Starting stage ID " + id + " (input: " + hasInput + " ).");

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                if (hasInput)
                    rebuffer();
                else
                    process(null);
            }
        };

        thread = new Thread(runnable);
        thread.start();

        // call start() of attached consumer
        for (Stage consumer : consumerSet) {
            consumer.start();
        }

    }


    void stop() {
        thread.interrupt();
    }


    void rebuffer() {

        System.out.println("----------> ID: " + id);

        boolean abort = false;
        int samples = 0;
        int channels = 2;
        float[][] buffer = new float[channels][blockSize];

        Log.d(LOG, "Start processing");

        while (!Thread.currentThread().isInterrupted() & !abort) {

            float[][] dataIn;

            dataIn = receive();

            if (dataIn != null) {

                int k = 0;
                int m = 0;

                try {
                    for (k = 0; k < dataIn[0].length; k++) {
                        for (m = 0; m < dataIn.length; m++) {
                            buffer[m][samples] = dataIn[m][k];
                        }
                        samples++;

                        if (samples >= blockSize) {

                            process(buffer);

                            samples = blockSize - hopSize;

                            for (int i = 0; i < dataIn.length; i++) {
                                System.arraycopy(buffer[i], hopSize, buffer[i], 0, blockSize - hopSize);
                            }
                        }
                    }
                } catch(Exception e) {
                    System.out.println("<-------------------");
                    System.out.println("buffer: " + buffer.length + "|" + buffer[0].length + " dataIn: " + dataIn.length + "|" + dataIn[0].length);
                    System.out.println(e.toString());
                    System.out.println("---> Line: " + e.getStackTrace()[0].getLineNumber());
                    System.out.println("--->" + samples + " | " + m + " | " + k);
                    System.out.println("ID: " + id);
                }

            } else {
                abort = true;
            }
        }

        Log.d(LOG, "Stopped consuming");

    }


    float[][] receive() {

        int timeout = 1000;

        try {

            return inQueue.poll(timeout, TimeUnit.MILLISECONDS);


            // rebuffer and call processing?


        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.d("Stage", "No elements in queue for " + timeout + " ms. Empty?");
            return null;
        }
    }


    void send(float[][] data) {

        try {
            for (LinkedBlockingQueue queue : outQueue) {
                queue.put(data);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    void addConsumer(Stage consumer) {

        // create new queue for consumer
        LinkedBlockingQueue queue = new LinkedBlockingQueue<>();

        // set queue in new consumer
        consumer.setInQueue(queue);

        // add queue to output queues of this stage
        outQueue.add(queue);

        // add new consumer to consumers for this stage
        consumerSet.add(consumer);
    }

    void setInQueue(LinkedBlockingQueue inQueue) {

        this.inQueue = inQueue;
    }

    protected abstract void process(float[][] buffer);

    protected void cleanup() {}

}
