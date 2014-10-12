package eval.wit.ai.myapplication;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.IOException;
import java.io.InputStream;

import ai.wit.sdk.IWitCoordinator;
import ai.wit.sdk.WitMic;

public class MyActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener , IWitCoordinator {

    public static String TAG = "wear";

    private TextView mTextView;
    public GoogleApiClient _gac;
    WitMic _witMic;
    Button _pushButton;
    ForwardAudioSampleThread _ft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                _pushButton = (Button) stub.findViewById(R.id.push_button);
                _pushButton.setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v) {
                        buttonPressed(v);
                    }
                });
            }
        });
        _gac = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        Log.d(TAG, "Lets go!");

    }

    @Override
    protected void onStart()
    {
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

    public void sendBytesToHandled(byte[] bytes, int n, int index)
    {
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/speech" + Math.random());
        final DataMap map = putRequest.getDataMap();
        map.putInt("n", n);
        map.putInt("index", index);
        map.putByteArray("data", bytes);

        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    public void sendStart()
    {
        Log.d("VoiceForce", "Sending listening to the phone");
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/start"+ Math.random());
        final DataMap map = putRequest.getDataMap();
        map.putDouble("c", Math.random());
        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "OnDataChange!");
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
        mTextView.setText("Listening...");
    }

    public void micStopListening()
    {
        _ft.interrupt(); // stop sending bytes to phones
        _witMic.stopRecording(); // stop recording
        sendStop(); // send stop to phone
        mTextView.setText("Stopped!");
    }

    @Override
    public void stopListening() {
        // vad stopped
        Log.d(TAG, "Stop recording now");
        micStopListening();
    }

    public void sendStop()
    {
        Log.d("VoiceForce", "Sending stop to the phone");
        final PutDataMapRequest putRequest = PutDataMapRequest.create("/stop");
        final DataMap map = putRequest.getDataMap();
        map.putDouble("c", Math.random());
        Wearable.DataApi.putDataItem(_gac, putRequest.asPutDataRequest());
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
