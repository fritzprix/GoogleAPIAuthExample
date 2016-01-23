package com.example.innocentevil.youtuber;

import android.accounts.Account;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.innocentevil.youtuber.fragment.OAuthDialogFragment;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTubeScopes;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * Created by innocentevil on 16. 1. 22.
 */
public class GoogleAuthenticator implements Handler.Callback,AuthorizationCodeFlow.CredentialCreatedListener,OAuthDialogFragment.OAuthDialogResultListener {

    protected static String TAG;
    private static HttpTransport HTTP_TRANSPORT;
    private static JsonFactory JSON_FACTORY;
    private static final String TOKEN_RESPONSE = GoogleAuthenticator.class.getCanonicalName()+".TOKEN";

    static {
        TAG = GoogleAuthenticator.class.getCanonicalName();
        HTTP_TRANSPORT = new NetHttpTransport();
        JSON_FACTORY = new JacksonFactory();
    }

    private static final String CLIENT_ID = "YOUR CLIENT ID";
    private static final String REDIRECT_URI = /*"urn:ietf:wg:oauth:2.0:oob"*/"http://localhost";

    /** Manage your YouTube account. */
    public static final String YOUTUBE = "https://www.googleapis.com/auth/youtube";

    /** Manage your YouTube account. */
    public static final String YOUTUBE_FORCE_SSL = "https://www.googleapis.com/auth/youtube.force-ssl";

    /** View your YouTube account. */
    public static final String YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly";

    /** Manage your YouTube videos. */
    public static final String YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload";

    /** View and manage your assets and associated content on YouTube. */
    public static final String YOUTUBEPARTNER = "https://www.googleapis.com/auth/youtubepartner";

    /** View private information of your YouTube channel relevant during the audit process with a YouTube partner. */
    public static final String YOUTUBEPARTNER_CHANNEL_AUDIT = "https://www.googleapis.com/auth/youtubepartner-channel-audit";

    private static final int MSG_REQUEST_AUTHORIZATION = 0x1001;
    private static final int MSG_REQUEST_REFRESH_TOKEN = 0x1003;
    private static final int MSG_SHOW_AUTH_DIALOG = 0x2001;
    private static final int MSG_REQUEST_NEW_TOKEN = 0x1002;

    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_EXPIRE_TIME = "expires_in";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_TOKEN_TYPE = "token_type";

    private HandlerThread worker;
    private Messenger workerMessenger;
    private Messenger uiMessenger;
    private WeakReference<Context> mWcontext;
    private Account[] googleAccounts;
    private FragmentManager fragmentManager;
    private GoogleAuthorizationCodeFlow mAuthFlow;
    private YoutubeAuthListener mAuthListener;

    interface YoutubeAuthListener {
        void onAccessTokenAvailable(String token);
    }


    public GoogleAuthenticator(Context context, @NonNull YoutubeAuthListener listener) {

        mWcontext = new WeakReference<Context>(context);
        Activity activity = (Activity) context;
        fragmentManager = activity.getFragmentManager();
        mAuthListener = listener;

        GoogleAuthorizationCodeFlow.Builder builder = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, CLIENT_ID, null, Arrays.asList(YouTubeScopes.YOUTUBE, YouTubeScopes.YOUTUBE_UPLOAD))
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .setCredentialCreatedListener(this);
        mAuthFlow = builder.build();
    }

    public GoogleAuthenticator begin(){
        worker = new HandlerThread("authworker");
        worker.start();
        Handler handler = new Handler(worker.getLooper(),this);
        workerMessenger = new Messenger(handler);
        uiMessenger = new Messenger(new Handler(this));
        return this;
    }

    @Override
    public void onCredentialCreated(Credential credential, TokenResponse tokenResponse) throws IOException {
    }

    public void requestToken(){
        Message msg = Message.obtain();
        JSONObject tokenJson = restoreTokenResponse();
        if(tokenJson != null && tokenJson.has(KEY_ACCESS_TOKEN) && tokenJson.has(KEY_REFRESH_TOKEN)){
            // have valid access token
            // refresh token
            msg.what = MSG_REQUEST_REFRESH_TOKEN;
            msg.obj = tokenJson;
        } else {
            // don't have valid token
            // try to get new token
            msg.what = MSG_REQUEST_AUTHORIZATION;
        }
        try{
            workerMessenger.send(msg);
        }catch(RemoteException e) {
            Log.e(TAG,e.getLocalizedMessage());
        }
    }

    public void end(){
        worker.quitSafely();
        try {
            worker.join();
        }catch(InterruptedException e) { }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch(msg.what){
            case MSG_REQUEST_AUTHORIZATION:

                String url = mAuthFlow.newAuthorizationUrl().setRedirectUri(REDIRECT_URI).build();
                msg = Message.obtain();
                msg.obj = url;
                msg.what = MSG_SHOW_AUTH_DIALOG;

                try{
                    uiMessenger.send(msg);
                }catch(RemoteException e) {
                    Log.e(TAG,e.getLocalizedMessage());
                }
                return true;
            case MSG_SHOW_AUTH_DIALOG:
                DialogFragment fragment = OAuthDialogFragment.getInstance((String) msg.obj,this);
                fragment.show(fragmentManager,"OAUTH_DIALOG");
                return true;
            case MSG_REQUEST_NEW_TOKEN:
                GoogleAuthorizationCodeTokenRequest newTokenRequest = mAuthFlow.newTokenRequest((String) msg.obj).
                        setRedirectUri("http://localhost").
                        setGrantType("authorization_code");
                try {
                    TokenResponse newtoken = newTokenRequest.execute();
                    Log.e(TAG, String.format("new Token Available : %s", newtoken.toPrettyString()));

                    JSONObject tokenJson = new JSONObject();
                    tokenJson.put(KEY_ACCESS_TOKEN, newtoken.getAccessToken())
                    .put(KEY_REFRESH_TOKEN,newtoken.getRefreshToken())
                    .put(KEY_EXPIRE_TIME,newtoken.getExpiresInSeconds())
                    .put(KEY_TOKEN_TYPE, newtoken.getTokenType());

                    saveTokenResponse(tokenJson);
                    mAuthListener.onAccessTokenAvailable(tokenJson.getString(KEY_ACCESS_TOKEN));


                }catch(JSONException e) {
                    Log.e(TAG,e.getLocalizedMessage());
                }catch(IOException e){
                    Log.e(TAG,e.getLocalizedMessage());
                }
                return true;
            case MSG_REQUEST_REFRESH_TOKEN:
                JSONObject tokenJson = (JSONObject) msg.obj;

                try {
                    GoogleRefreshTokenRequest refreshTokenRequest = new GoogleRefreshTokenRequest(HTTP_TRANSPORT, JSON_FACTORY, tokenJson.getString(KEY_REFRESH_TOKEN), CLIENT_ID, null);
                    TokenResponse response = refreshTokenRequest.execute();
                    tokenJson.put(KEY_ACCESS_TOKEN, response.getAccessToken());
                    Log.e(TAG, String.format("refreshed Token Available : %s", tokenJson.toString()));

                    saveTokenResponse(tokenJson);
                    mAuthListener.onAccessTokenAvailable(tokenJson.getString(KEY_ACCESS_TOKEN));

                }catch(JSONException e) {

                }catch(IOException e) {
                    Log.e(TAG,e.getLocalizedMessage());
                }
                return true;
        }
        return false;
    }

    private void requestAccessToken(String auth_code){
        Message msg = Message.obtain();
        msg.what = MSG_REQUEST_NEW_TOKEN;
        msg.obj = auth_code;
        try{
            workerMessenger.send(msg);
        }catch(RemoteException e ){
            Log.e(TAG,e.getLocalizedMessage());
        }
    }

    private void saveTokenResponse(JSONObject tokenJson) throws IOException {
        Log.e(TAG,String.format("Token Saved : %s",tokenJson.toString()));
        SharedPreferences pref = mWcontext.get().getSharedPreferences(Constants.PREF_GOOGLE_ID, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(TOKEN_RESPONSE,tokenJson.toString());
        editor.commit();
    }

    private JSONObject restoreTokenResponse() {
        SharedPreferences pref = mWcontext.get().getSharedPreferences(Constants.PREF_GOOGLE_ID, Context.MODE_PRIVATE);
        String tokenString = pref.getString(TOKEN_RESPONSE, "");
        if(tokenString.length() == 0)
            return null;
        try{
            JSONObject jobs  = new JSONObject(tokenString);
            return jobs;
        }catch(JSONException e) {
            return null;
        }
    }

    @Override
    public void onAuthorizationCodeAvailable(String authCode) {
        Log.e(TAG, "Authorization code : " + authCode);
        requestAccessToken(authCode);
    }

}
