package com.example.innocentevil.youtuber.fragment;

import android.app.DialogFragment;
import android.app.Fragment;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.example.innocentevil.youtuber.R;

/**
 * Created by innocentevil on 16. 1. 22.
 */
public class OAuthDialogFragment extends DialogFragment {

    public static final String KEY_OAUTH_URL = OAuthDialogFragment.class.getCanonicalName() + ".URL";
    private boolean authcomplete;
    private WebView mWebView;
    private OAuthDialogResultListener mListener;

    public static DialogFragment getInstance(String ouathurl,@NonNull OAuthDialogResultListener listener){
        OAuthDialogFragment fragment = new OAuthDialogFragment();
        fragment.mListener = listener;
        Bundle data = new Bundle();
        data.putString(KEY_OAUTH_URL,ouathurl);
        fragment.setArguments(data);
        return fragment;
    }

    public OAuthDialogFragment(){
        super();
        authcomplete = false;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.oauth_dialog_layout, container, false);
        mWebView = (WebView) view.findViewById(R.id.oauth_dialog_wv);
        return  view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String ouathurl = getArguments().getString(KEY_OAUTH_URL);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (url.contains("?code=") && !authcomplete) {
                    authcomplete = true;
                    Uri uri = Uri.parse(url);
                    String authtoken = uri.getQueryParameter("code");
                    mListener.onAuthorizationCodeAvailable(authtoken);
                    dismiss();
                }
            }
        });
        mWebView.loadUrl(ouathurl);
    }
    public interface OAuthDialogResultListener {
        void onAuthorizationCodeAvailable(String token);
    }
}
