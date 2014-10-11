package eval.wit.ai.myapplication;

/**
 * Created by aric on 10/10/14.
 */

import android.util.Log;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

public class Listen  extends  WearableListenerService {


    @Override
    public void onCreate()
    {
        Log.d("Handled", "listener created");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents)
    {
        Log.d("Handled", "data changed!");
    }

    @Override
    public void onPeerConnected(Node peer)
    {
        Log.d("Handled", "Peer connected");
    }

    @Override
    public void onPeerDisconnected(Node peer)
    {
        Log.d("Handled", "Peer disconnected!");
    }


}

