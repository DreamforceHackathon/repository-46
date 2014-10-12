package eval.wit.ai.myapplication;

import android.util.Log;

import com.google.gson.JsonElement;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteOrder;
import java.util.HashMap;

import ai.wit.sdk.IWitListener;
import ai.wit.sdk.Wit;
import ai.wit.sdk.WitMic;

/**
 * Created by aric on 10/11/14.
 */
public class WitAudioPiper implements IWitListener {

    PipedInputStream _in;
    PipedOutputStream _out;
    Wit _wit;
    int _counter = 0;

    public WitAudioPiper()
    {
        _in = new PipedInputStream();
        _out = new PipedOutputStream();
        _wit = new Wit("ZPSERSZGFU45YFAKT2DEXTHWYUXNNJJ5", this);
        try {
            _in.connect(_out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        _wit.streamRawAudio(_in, "signed-integer", 16, WitMic.SAMPLE_RATE, ByteOrder.LITTLE_ENDIAN);
    }


    public PipedInputStream getInputStream()
    {
        return _in;
    }

    public void gotSamples(byte[] buffer) throws IOException {

        _counter++;
        if (_counter > 100) {
            _out.close();
        }
    }

    @Override
    public void witDidGraspIntent(String s, HashMap<String, JsonElement> stringJsonElementHashMap, String s2, double v, Error error) {
        Log.d("handled", "DID GRASP INTENT FROM WATCH!!");
    }

    @Override
    public void witDidStartListening() {

    }

    @Override
    public void witDidStopListening() {

    }
}
