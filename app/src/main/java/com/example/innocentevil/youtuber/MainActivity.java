package com.example.innocentevil.youtuber;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity implements GoogleAuthenticator.YoutubeAuthListener {

    private GoogleAuthenticator mAuthenticator;
    protected static String TAG = MainActivity.class.getCanonicalName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuthenticator = new GoogleAuthenticator(this,this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        mAuthenticator.begin().requestToken();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAuthenticator.end();
    }

    @Override
    public void onAccessTokenAvailable(String token) {
        Log.e(TAG,"Token is available " + token);
    }
}
