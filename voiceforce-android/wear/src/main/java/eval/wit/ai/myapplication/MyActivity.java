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
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.common.api.GoogleApiClient;


import java.io.IOException;
import java.io.InputStream;

import ai.wit.sdk.IWitCoordinator;
import ai.wit.sdk.WitMic;

public class MyActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener , IWitCoordinator {

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

        Log.d("Wear", "Lets go!");

        try {
            _witMic = new WitMic(this, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        Log.d("Wear", "onStart");
        _gac.connect();
    }

    @Override
    public void onConnected(android.os.Bundle bundle) {
        Log.d("Wear", "onConnected");
        sendDataTest();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Wear", "onSuspended");
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult connectionResult) {
        Log.d("Wear", "onConnectionFailed");
    }

    public void sendDataTest()
    {
        byte bytes[] = new byte[2];
        bytes[0] = (byte) 1;
        bytes[0] = (byte) 6;
        PutDataRequest pdr = PutDataRequest.create("/witaudioooo");
        pdr.setData(bytes);
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(_gac, pdr);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(final DataApi.DataItemResult result) {

                if(result.getStatus().isSuccess()) {
                    Log.d("Wear", "Data item set: " + result.getDataItem().getUri());
                } else {
                    Log.d("Wear", "Something went wrong setting data: " + result.getDataItem().getUri());
                }
            }
        });
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d("wear", "OnDataChange!");
    }

    @Override
    public void stopListening() {
        Log.d("wear", "Stop recording now");
        _witMic.stopRecording();
        mTextView.setText("Stopped!");

    }

    public void buttonPressed(View v) {
        if (_witMic.isRecording() == false) {
            _witMic.startRecording();
            InputStream inputStream = _witMic.getInputStream();
            ForwardAudioSampleThread ft = new ForwardAudioSampleThread(inputStream);
            ft.start();
            mTextView.setText("Listening...");
        } else {
            _witMic.stopRecording();
            mTextView.setText("Stopped!");
        }
    }

    class ForwardAudioSampleThread extends Thread {
        InputStream _in;

        public ForwardAudioSampleThread(InputStream in) {
            _in = in;
        }

        @Override
        public void run() {
            int n;
            byte[] buffer = new byte[1024];

            try {
                while ((n = _in.read(buffer)) > 0) {
                    Log.d("wear", "Got "+ n +" bytes from the microphone");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


}
