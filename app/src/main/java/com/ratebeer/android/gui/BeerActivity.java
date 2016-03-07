package com.ratebeer.android.gui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jakewharton.rxbinding.support.v7.widget.RxToolbar;
import com.jakewharton.rxbinding.view.RxView;
import com.ratebeer.android.R;
import com.ratebeer.android.Session;
import com.ratebeer.android.api.Api;
import com.ratebeer.android.api.ImageUrls;
import com.ratebeer.android.api.model.BeerRating;
import com.ratebeer.android.db.Beer;
import com.ratebeer.android.db.Db;
import com.ratebeer.android.db.Rating;
import com.ratebeer.android.gui.lists.BeerRatingsAdapter;
import com.ratebeer.android.gui.widget.Animations;
import com.ratebeer.android.gui.widget.Images;

import java.util.List;

import rx.Observable;

public final class BeerActivity extends RateBeerActivity {

	private ProgressBar loadingProgress;
	private View detailsLayout;
	private FloatingActionButton rateButton;

	public static Intent start(Context context, long beerId) {
		return new Intent(context, BeerActivity.class).putExtra("beerId", beerId);
	}

	@SuppressLint("PrivateResource")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_beer);

		// Set up toolbar
		Toolbar mainToolbar = (Toolbar) findViewById(R.id.main_toolbar);
		mainToolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
		mainToolbar.inflateMenu(R.menu.menu_refresh);
		RxToolbar.navigationClicks(mainToolbar).subscribe(ignore -> navigateUp());
		RxToolbar.itemClicks(mainToolbar).filter(item -> item.getItemId() == R.id.menu_refresh).subscribe(item -> {
			Animations.fadeFlip(loadingProgress, detailsLayout);
			refresh(true);
		});

		loadingProgress = (ProgressBar) findViewById(R.id.loading_progress);
		detailsLayout = findViewById(R.id.details_layout);
		rateButton = (FloatingActionButton) findViewById(R.id.rate_button);
		rateButton.setVisibility(View.GONE);

	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh(false);
	}

	private void refresh(boolean forceFresh) {

		// Load beer and ratings from database or live
		long beerId = getIntent().getLongExtra("beerId", 0);
		Db.getBeer(this, beerId, forceFresh).compose(onIoToUi()).compose(bindToLifecycle())
				.subscribe(this::showBeer, e -> Snackbar.show(this, R.string.error_connectionfailure));

		Observable<BeerRating> ratings =
				Api.get().getBeerRatings(beerId).filter(beerRating -> !Session.get().isLoggedIn() || beerRating.userId != Session.get().getUserId());
		if (Session.get().isLoggedIn())
			ratings = ratings.startWith(Db.getUserRating(this, beerId, Session.get().getUserId()).map(this::localToBeerRating));
		ratings.toList().compose(onIoToUi()).compose(bindToLifecycle()).subscribe(this::showRatings, Throwable::printStackTrace);

	}

	private void showBeer(Beer beer) {
		ImageView photoImage = (ImageView) findViewById(R.id.backdrop_image);
		Images.with(this).loadBeer(beer._id, true).fit().centerCrop().noPlaceholder().into(photoImage);
		String brewerStyleText = getString(R.string.beer_stylebrewer, beer.styleName, beer.brewerName);
		SpannableStringBuilder brewerStyleMarkup = new SpannableStringBuilder(brewerStyleText);
		int styleStart = brewerStyleText.indexOf(beer.styleName);
		int styleEnd = styleStart + beer.styleName.length();
		int brewerStart = brewerStyleText.indexOf(beer.brewerName);
		int brewerEnd = brewerStart + beer.brewerName.length();
		brewerStyleMarkup.setSpan(new StyleSpan(Typeface.BOLD), styleStart, styleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		brewerStyleMarkup.setSpan(new StyleSpan(Typeface.BOLD), brewerStart, brewerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		((TextView) findViewById(R.id.brewer_name_text)).setText(brewerStyleMarkup);
		((TextView) findViewById(R.id.beer_name_text)).setText(beer.name);
		((TextView) findViewById(R.id.mark_overall_text)).setText(beer.getOverallPercentileString());
		((TextView) findViewById(R.id.mark_style_text)).setText(beer.getStylePercentileString());
		((TextView) findViewById(R.id.mark_count_text)).setText(beer.getRateCountString());
		((TextView) findViewById(R.id.mark_abv_text)).setText(beer.getAlcoholString());
		((TextView) findViewById(R.id.mark_ibu_text)).setText(beer.getIbuString());
		((TextView) findViewById(R.id.mark_calories_text)).setText(beer.getCaloriesString());
		Animations.fadeFlip(detailsLayout, loadingProgress);
		rateButton.setVisibility(Session.get().isLoggedIn() ? View.VISIBLE : View.GONE);
		RxView.clicks(rateButton).subscribe(clicked -> startActivity(RateActivity.start(this, beer)));
		RxView.clicks(photoImage).subscribe(clicked -> startActivity(PhotoActivity.start(this, beer._id)));
	}

	private void showRatings(List<BeerRating> ratings) {
		RecyclerView ratingsList = (RecyclerView) findViewById(R.id.ratings_list);
		ratingsList.setLayoutManager(new LinearLayoutManager(this));
		ratingsList.setAdapter(new BeerRatingsAdapter(ratings));
	}

	private BeerRating localToBeerRating(Rating rating) {
		// Mimic a recent rating object to show in the latest beer ratings list
		BeerRating beerRating = new BeerRating();
		// Take the stored rating of the logged in user
		beerRating.ratingId = rating.ratingId == null ? 0: rating.ratingId.intValue();
		beerRating.aroma = rating.aroma;
		beerRating.flavor = rating.flavor;
		beerRating.appearance = rating.appearance;
		beerRating.mouthfeel = rating.mouthfeel;
		beerRating.overall = rating.overall;
		beerRating.total = rating.total;
		beerRating.comments = rating.comments;
		beerRating.timeEntered = rating.timeEntered;
		beerRating.timeUpdated = rating.timeUpdated;
		// Combine with the up-to-date user details from the session
		beerRating.userId = Session.get().getUserId();
		beerRating.userName = Session.get().getUserName();
		beerRating.userRateCount = Session.get().getUserRateCount();
		return beerRating;
	}

	public void uploadFindBeer(View view) {}
}
