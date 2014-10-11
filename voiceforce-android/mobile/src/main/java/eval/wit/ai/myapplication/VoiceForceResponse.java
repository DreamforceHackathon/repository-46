package eval.wit.ai.myapplication;

import com.google.gson.annotations.SerializedName;

/**
 * Created by oliv on 10/10/14.
 */
public class VoiceForceResponse {
    @SerializedName("state")
    private String _state;

    @SerializedName("text")
    private String _text;


    public String get_text() {
        return _text;
    }

    public String get_state() {
        return _state;
    }
}
