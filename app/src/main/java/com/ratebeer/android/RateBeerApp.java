package com.ratebeer.android;

import android.app.Application;

public class RateBeerApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		Session.get().init(this);

		//Picasso.setSingletonInstance(new Picasso.Builder(this).indicatorsEnabled(BuildConfig.DEBUG).loggingEnabled(BuildConfig.DEBUG).build());
	}

}
