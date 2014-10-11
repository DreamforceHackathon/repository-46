package eval.wit.ai.myapplication;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.common.api.GoogleApiClient;


import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

import ai.wit.sdk.IWitCoordinator;
import ai.wit.sdk.WitMic;

public class MyActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener , IWitCoordinator {

    public static String TAG = "wear";

    private TextView mTextView;
    public GoogleApiClient _gac;
    WitMic _witMic;
    Button _pushButton;

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

    public void sendBytesToHandled(byte[] bytes)
    {

        final PutDataMapRequest putRequest = PutDataMapRequest.create("/SAMPLE"+Math.random());
        final DataMap map = putRequest.getDataMap();

        map.putByteArray("witaudiodata", bytes);

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

        mTextView.setText("Stopped!");

    }

    public void buttonPressed(View v) {

        if (_witMic == null || _witMic.isRecording() == false) {
            try {
                micStartListening();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            micStopListening();
        }
    }

    public void micStartListening() throws IOException {
        _witMic = new WitMic(this, true);
        _witMic.startRecording();
        InputStream inputStream = _witMic.getInputStream();
        ForwardAudioSampleThread ft = new ForwardAudioSampleThread(inputStream, this);
        ft.start();
        mTextView.setText("Listening...");
    }

    public void micStopListening()
    {
        _witMic.stopRecording();
        mTextView.setText("Stopped!");
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
            byte[] buffer = new byte[1024];

            try {
                while ((n = _in.read(buffer)) > 0) {
                    sendBytesToHandled(buffer);
//                    Log.d(TAG, "Got "+ n +" bytes from the microphone");
//                    Wearable.DataApi.addListener(_gac, _my);
//                    NodeApi.GetConnectedNodesResult nodes =
//                            Wearable.NodeApi.getConnectedNodes(_gac).await();
//                    for (Node node : nodes.getNodes()) {
//                        Log.d(TAG, "Node connected: "+node.getDisplayName());
//                        for (int i = 0; i < 10; i++) {
//
//                            Wearable.MessageApi.sendMessage(_gac, node.getId(), "soundssamples", buffer);
//                            Log.d(TAG, "sending audio bytes!");
//                        }
//                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


}
