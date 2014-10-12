package eval.wit.ai.myapplication;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.wearable.view.CardFragment;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import ai.wit.sdk.IWitCoordinator;
import ai.wit.sdk.WitMic;

public class MyActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener, IWitCoordinator {

    public static String TAG = "wear";

    public GoogleApiClient _gac;
    WitMic _witMic;
    ForwardAudioSampleThread _ft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        _gac = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        Log.d(TAG, "Lets go!");

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        _gac.connect();
        try {
            _witMic = new WitMic(this, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals("text")) {
            try {
                String text = new String(messageEvent.getData(), "UTF-8");
                Log.d("sdf", "Received" + text);
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                CardFragment cardFragment = CardFragment.create("SFDC",
                        text,
                        R.drawable.microphone);
                fragmentTransaction.add(R.id.frame_layout, cardFragment);
                fragmentTransaction.commit();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public void onConnected(android.os.Bundle bundle) {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onSuspended");
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    public void sendBytesToHandled(byte[] bytes, int n, int index) {
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/speech" + Math.random());
        final DataMap map = putRequest.getDataMap();
        map.putInt("n", n);
        map.putInt("index", index);
        map.putByteArray("data", bytes);

        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    public void sendStart() {
        Log.d("VoiceForce", "Sending listening to the phone");
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/start" + Math.random());
        final DataMap map = putRequest.getDataMap();
        map.putDouble("c", Math.random());
        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    public void sendStop() {
        Log.d("VoiceForce", "Sending stop to the phone");
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/stop" + Math.random());
        final DataMap map = putRequest.getDataMap();
        map.putDouble("c", Math.random());
        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "OnDataChange!");
    }

    @Override
    public void stopListening() {
        Log.d(TAG, "Stop recording now");
        _witMic.stopRecording();
    }

    public void buttonPressed(View v) {
        Log.d("VoiceForce", "Button pressed " + (_witMic == null || !_witMic.isRecording()));
        if (_witMic == null || !_witMic.isRecording()) {
            micStartListening();
        } else {
            micStopListening();
        }
    }

    public void micStartListening() {
        Log.d("VoiceForce", "Starting listening");
        try {
            _witMic = new WitMic(this, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        _witMic.startRecording();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendStart();
            }
        });
        InputStream inputStream = _witMic.getInputStream();

        _ft = new ForwardAudioSampleThread(inputStream, this);
        _ft.start();
    }

    public void micStopListening() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendStop();
            }
        });
        _witMic.stopRecording();
        _ft.interrupt();
    }

    class ForwardAudioSampleThread extends Thread {
        InputStream _in;
        MyActivity _my;

        public ForwardAudioSampleThread(InputStream in, MyActivity my) {
            _in = in;
            _my = my;
        }

        @Override
        public void run() {
            int n;
            int count = 0;
            byte[] buffer = new byte[1024];
            try {
                while (-1 != (n = _in.read(buffer))) {
                    Log.d("VoiceForce", "" + ++count);
                    sendBytesToHandled(buffer, n, count);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
