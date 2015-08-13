package com.acrcloud.rec.demo;

import java.io.File;

import com.acrcloud.rec.sdk.ACRCloudConfig;
import com.acrcloud.rec.sdk.ACRCloudClient;
import com.acrcloud.rec.sdk.IACRCloudListener;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity implements IACRCloudListener{

	private ACRCloudClient mClient;
	private ACRCloudConfig mConfig;
	
	private TextView mVolume, mResult, mArtistName;
    private WebView mWebView;
	
	private boolean mProcessing = false;
	private boolean initState = false;
	
	private String path = "";
    private static String mBaseGeniusUrlPath = "http://genius.com/";
    private static String sBaseGoogleSearchApiUrl = "https://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=genius%20";
    private static final String HTTP_SPACE = "%20";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		path = Environment.getExternalStorageDirectory().toString()
				+ "/acrcloud/model";
		
		File file = new File(path);
		if(!file.exists()){
			file.mkdirs();
		}		
			
		mVolume = (TextView) findViewById(R.id.volume);
		mResult = (TextView) findViewById(R.id.result);
        mArtistName = (TextView) findViewById(R.id.artist_name);
        mWebView = (WebView) findViewById(R.id.genius_url_webview);
        mWebView.setHorizontalScrollBarEnabled(false);

        mWebView.loadUrl(mBaseGeniusUrlPath);
        mWebView.setWebViewClient(new MyWebViewClient());
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
		
		Button startBtn = (Button) findViewById(R.id.start);
		startBtn.setText(getResources().getString(R.string.start));
		Button cancelBtn = (Button) findViewById(R.id.cancel);
		cancelBtn.setText(getResources().getString(R.string.cancel));
		
		findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				start();
			}
		});
		
		findViewById(R.id.cancel).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						cancel();
					}
				});
	}

	
	public void start() {	
		mVolume.setText("");
		mResult.setText("Search...");
        mArtistName.setText("");
		if(!this.initState) {
			this.mConfig = new ACRCloudConfig();

			this.mConfig.acrcloudListener = this;
			this.mConfig.context = this;
			this.mConfig.host = ApiKeys.getConfigHost();
			this.mConfig.accessKey = ApiKeys.getAccessKey();
			this.mConfig.accessSecret = ApiKeys.getAccessSecret();

			this.mConfig.reqMode = ACRCloudConfig.ACRCloudRecMode.REC_MODE_REMOTE;
			this.mClient = new ACRCloudClient();	
			this.initState = this.mClient.initWithConfig(this.mConfig);
			if (!this.initState) {
				Toast.makeText(this, "init error", Toast.LENGTH_SHORT).show();
				return;
			}			
		}
		
		if (!mProcessing) {
			mProcessing = true;
			if (!this.mClient.startRecognize()) {
				mProcessing = false;
				mResult.setText("start error!");
			}
		}
	}
	
	protected void cancel() {
		if (mProcessing) {
            this.mClient.stop();
            mResult.setText("");
        }
		mProcessing = false;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onResult(String result) {
        Log.d("got result","woo!");
        Log.d("result:",result);
        try{
            JSONObject resAsJson = new JSONObject(result);
            JSONObject status = resAsJson.getJSONObject("status");
            int code = status.getInt("code");
            if(code == 1001){
                //fail: 08-12 22:41:38.430  18832-18832/com.acrcloud.rec.demo E/resAsJson﹕ {"status":{"msg":"No result","version":"1.0","code":1001}}
                String msg = status.getString("msg");
                Toast.makeText(getApplicationContext(), msg,
                        Toast.LENGTH_LONG).show();
            }else{
                JSONObject metaDataObject = resAsJson.getJSONObject("metadata");
                JSONArray mussicArray = metaDataObject.getJSONArray("music");
                JSONObject dataHousingObject = mussicArray.getJSONObject(0);
                String songTitle = dataHousingObject.getString("title");
                JSONArray artistsArray = dataHousingObject.getJSONArray("artists");
                JSONObject firstName = artistsArray.getJSONObject(0);
                StringBuilder artists = new StringBuilder(firstName.getString("name"));
                artistsArray.getString(0);
                if(artistsArray.length() > 0){
                    for(int i = 1; i < artistsArray.length(); i++){
                        artists.append(",").append(artistsArray.getJSONObject(i).getString("name"));
                    }
                }

                String artistString = artists.toString();
                String[] splitArtistString = artistString.split(" ");
                String firstNameText = splitArtistString[0];
                String lastNameText = splitArtistString[1];
                Log.e("artist",artistString);

                mResult.setText(songTitle);
                mArtistName.setText("by " + artistString);

                /*
                mBaseGeniusUrlPath += firstNameText+"-"+lastNameText+"-";

                String[] splitSongString = songTitle.split(" ");
                for(int i = 0; i < splitSongString.length; i++){
                    mBaseGeniusUrlPath += (splitSongString[i]+"-");
                }


                mBaseGeniusUrlPath += "lyrics";
                Log.e("url",mBaseGeniusUrlPath);
                */

                AsyncHttpClient client = new AsyncHttpClient();
                String urlToGet = buildGoogleSearchUrl(songTitle, firstNameText, lastNameText);
                Log.e("url:",urlToGet);
                client.get(urlToGet, new JsonHttpResponseHandler(){
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                        try{
                            Log.e("response:",response.toString());
                            JSONObject responseData = response.getJSONObject("responseData");
                            JSONArray results = responseData.getJSONArray("results");
                            JSONObject itemToParse = results.getJSONObject(0);
                            String urlToLoad = itemToParse.getString("url");
                            mWebView.loadUrl(urlToLoad);
                        }catch (JSONException e){
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                        Log.e("failure:",errorResponse.toString());
                    }
                });
            }


        }catch (JSONException e){
            e.printStackTrace();
        }

		mProcessing = false;
		
		if (this.mClient != null) {
			this.mClient.stop();
			mProcessing = false;
		} 	
	}

    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.contains("com")) {
                // This is my web site, so do not override; let my WebView load the page
                return false;
            }
            // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }
    }



	@Override
	public void onVolumeChanged(double volume) {
		mVolume.setText(getResources().getString(R.string.volume) + volume);
	}
	
	@Override  
    protected void onDestroy() {  
        super.onDestroy();  
        Log.e("MainActivity", "release");
        if (this.mClient != null) {
        	this.mClient.release();
        	this.mClient = null;
        }
    }

    private String buildGoogleSearchUrl(String songTitle, String firstName, String lastName){
        String urlToSearch = sBaseGoogleSearchApiUrl+songTitle+HTTP_SPACE+firstName+HTTP_SPACE+lastName;

        return urlToSearch;
    }

}
