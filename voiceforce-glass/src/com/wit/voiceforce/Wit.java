package com.wit.voiceforce;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.glass.timeline.LiveCard;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.socketio.Acknowledge;
import com.koushikdutta.async.http.socketio.StringCallback;

import android.net.Uri;
import android.os.AsyncTask;
import android.widget.RemoteViews;

public class Wit extends AsyncTask<String, Void, String> {

	private final String access_token = "BZVYQEJWQYWUBH3VKRU7NFC7K7657XV3";
	
	private RemoteViews view;
	private int viewID;
	private LiveCard card;
	
	public Wit (RemoteViews view, int viewID, LiveCard card) {
		this.view = view;
		this.viewID = viewID;
		this.card = card;
	}
	
	@Override
	protected String doInBackground(String... queries) {
		try {
			return query(queries[0]);
		} catch (IOException e) {
			return e.getMessage();
		}
	}
	
	private String buildRequest(String wit) {
		JSONObject json = new JSONObject();
		try {
			JSONObject wit_msg = new JSONObject(wit);
			JSONObject outcome = wit_msg.getJSONArray("outcomes").getJSONObject(0);
			json.put("text", "Glass");
			json.put("entities", outcome.get("entities"));
			json.put("intent", outcome.get("intent"));
			//json.put("state", "");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return json.toString();
	}
	
	@Override
	protected void onPostExecute(String result) {
		//view.setTextViewText(viewID, result);
        //card.setViews(view);
		final String request = buildRequest(result);
		AsyncHttpClient.getDefaultInstance().websocket("ws://192.168.1.25:1337", "my-protocol", new WebSocketConnectCallback() {
		    @Override
		    public void onCompleted(Exception ex, WebSocket webSocket) {
		        if (ex != null) {
		            ex.printStackTrace();
		            return;
		        }
		        webSocket.send(request);
		        /*webSocket.setStringCallback(new StringCallback() {
					@Override
					public void onString(String arg0, Acknowledge arg1) {
						view.setTextViewText(viewID, arg0);
						card.setViews(view);
					}
		        });*/
		        webSocket.setDataCallback(new DataCallback() {
		        	@Override
		            public void onDataAvailable(DataEmitter de, ByteBufferList byteBufferList) {
		                //System.out.println("I got some bytes!");	                
		                String str = byteBufferList.readString();
		                view.setTextViewText(viewID, str);
		                card.setViews(view);
		                // note that this data has been read
		                byteBufferList.recycle();
		            }
		        });
		    }
		});
		
	}
	
	private String query(String q) throws IOException {
		HttpClient client = new DefaultHttpClient();
	    HttpGet get = new HttpGet("https://api.wit.ai/message?v=20141609&q=" + URLEncoder.encode(q, "UTF-8"));
	    get.setHeader("Authorization", "Bearer " + access_token);
	    HttpResponse response = client.execute(get);    
		BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = in.readLine()) != null) {
            result.append(line);
        }
        return result.toString();
	}
}
