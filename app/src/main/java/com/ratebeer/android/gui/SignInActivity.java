package com.ratebeer.android.gui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.ratebeer.android.R;
import com.ratebeer.android.api.Api;
import com.ratebeer.android.gui.widget.Animations;

public final class SignInActivity extends RateBeerActivity {

	public static Intent start(Context context, boolean withBackNavigation) {
		return new Intent(context, SignInActivity.class).putExtra("back_navigation", withBackNavigation);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signin);

		if (getIntent().getBooleanExtra("back_navigation", false))
			setupDefaultUpButton();
		else
			findViewById(R.id.main_toolbar).setVisibility(View.GONE);

	}

	public void signIn(View view) {

		View loginProgress = findViewById(R.id.login_progress);
		View decisionLayout = findViewById(R.id.decision_layout);
		String username = ((EditText) findViewById(R.id.username_edit)).getText().toString();
		String password = ((EditText) findViewById(R.id.password_edit)).getText().toString();

		if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
			Snackbar.show(this, R.string.error_nouserorpass);
			return;
		}

		Animations.fadeFlip(loginProgress, decisionLayout);

		Api.get().login(username, password)
				.compose(onIoToUi())
				.compose(bindToLifecycle())
				.subscribe(success -> {
					navigateUp(); // Restart main activity to refresh activities state
					finish();
				}, e -> {
					Snackbar.show(this, R.string.error_authenticationfailed);
					Animations.fadeFlip(decisionLayout, loginProgress);
				});
	}

	public void createAccount(View view) {
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Api.DOMAIN + "/newuser/")));
	}

}
