package cm.aptoide.pt;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aptoide.amethyst.models.EnumStoreTheme;
import com.aptoide.amethyst.models.search.SearchApk;
import com.aptoide.amethyst.models.search.SearchResults;
import com.aptoide.amethyst.ui.listeners.EndlessRecyclerOnScrollListener;
import com.aptoide.amethyst.utils.AptoideUtils;
import com.aptoide.amethyst.webservices.SearchRequest;
import com.aptoide.models.HeaderRow;
import com.aptoide.models.ProgressBarRow;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import java.util.List;

import butterknife.Bind;
import cm.aptoide.pt.adapter.BaseAdapter;
import cm.aptoide.pt.adapter.MoreSearchAdapter;
import cm.aptoide.pt.fragments.store.BaseWebserviceFragment;
import cm.aptoide.pt.ui.MoreActivity;

/**
 * Created by fabio on 20-11-2015.
 */
public class MoreSearchActivity extends MoreActivity {
    @Bind(R.id.appbar) AppBarLayout mAppBar;

    public static final String QUERY_BUNDLE_KEY = "search_query";
    private EnumStoreTheme storeTheme = null;

    @Override
    protected Fragment getFragment(Bundle args) {
        MoreSearchFragment fragment = new MoreSearchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mToolbar != null) {
            mToolbar.setLogo(null);
            mToolbar.setTitle(AptoideUtils.StringUtils.getFormattedString(this, R.string.search_activity_title, getIntent().getExtras().getString(QUERY_BUNDLE_KEY)));
            try {
                storeTheme = (EnumStoreTheme) getIntent().getExtras().get(SearchActivity.SEARCH_THEME);
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
            setupStoreTheme(storeTheme);
        }
    }

    private void setupStoreTheme(EnumStoreTheme storeTheme) {
        if (storeTheme != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(getResources().getColor(storeTheme.getColor700tint()));
            }
            mAppBar.setBackgroundColor(getResources().getColor(storeTheme.getStoreHeader()));
            mToolbar.setBackgroundColor(getResources().getColor(storeTheme.getStoreHeader()));
        }
    }

    static public class MoreSearchFragment extends BaseWebserviceFragment {
        @Bind(R.id.ic_search_button) ImageView searchButton;
        @Bind(R.id.search_text) EditText searchQuery;
        @Bind(R.id.no_search_results_layout) ScrollView noSearchResultLayout;

        @Override
        public void setLayoutManager(RecyclerView recyclerView) {
            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        }

        private String query;
        private String storeName;
        private boolean mLoading = false;

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            query = getArguments().getString(QUERY_BUNDLE_KEY);
            storeName = getArguments().getString(SearchActivity.SEARCH_SOURCE);
            super.onViewCreated(view, savedInstanceState);
            getRecyclerView().addOnScrollListener(new EndlessRecyclerOnScrollListener((LinearLayoutManager) getRecyclerView().getLayoutManager()) {
                @Override
                public void onLoadMore() {
                    mLoading = true;
                    displayableList.add(new ProgressBarRow(BUCKET_SIZE));
                    adapter.notifyItemInserted(adapter.getItemCount());
                    executeEndlessSpiceRequest();
                }

                @Override
                public int getOffset() {
                    return offset;
                }

                @Override
                public boolean isLoading() {
                    return mLoading;
                }
            });
        }


        @Override
        protected BaseAdapter getAdapter() {
            if (adapter == null) {
                return new MoreSearchAdapter(displayableList);
            }
            return adapter;
        }

        @Override
        protected String getBaseContext() {
            return SearchActivity.CONTEXT;
        }

        @Override
        protected void executeSpiceRequest(boolean useCache) {
            mLoading = true;
            long cacheExpiryDuration = useCache ? DurationInMillis.ONE_HOUR * 6 : DurationInMillis.ALWAYS_EXPIRED;
            spiceManager.execute(AptoideUtils.RepoUtils.buildSearchRequest(query, SearchRequest.SEARCH_LIMIT, SearchRequest.OTHER_REPOS_SEARCH_LIMIT, offset, storeName), query + offset + QUERY_BUNDLE_KEY + storeName, cacheExpiryDuration, listener);
        }


        RequestListener<SearchResults> listener = new RequestListener<SearchResults>() {
            @Override
            public void onRequestFailure(SpiceException spiceException) {
                handleErrorCondition(spiceException);
            }

            @Override
            public void onRequestSuccess(SearchResults searchResults) {
                handleSuccessCondition();
                adapter = getAdapter();
                getRecyclerView().setAdapter(adapter);

                List<SearchApk> apkList = searchResults.apkList;
                if (!apkList.isEmpty()) {
                    if (storeName != null && !TextUtils.isEmpty(storeName)) {
                        displayableList.add(new HeaderRow(AptoideUtils.StringUtils.getFormattedString(getContext(), R.string.results_in_store, storeName), false, BUCKET_SIZE));
                    } else {
                        displayableList.add(new HeaderRow(getString(R.string.results_subscribed), false, BUCKET_SIZE));
                    }
                    displayableList.addAll(apkList);
                }
                offset += apkList.size();
                getAdapter().notifyDataSetChanged();
                swipeContainer.setEnabled(false);
                mLoading = false;
                if (displayableList.size() <= 0) {
                    getRecyclerView().setVisibility(View.GONE);
                    noSearchResultLayout.setVisibility(View.VISIBLE);
                    searchQuery.setText(query);
                    searchButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startSearch();
                        }
                    });
                    searchQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                                startSearch();
                                return true;
                            }
                            return false;
                        }
                    });
                }
            }
        };

        private void startSearch() {
            Intent intent = new Intent(getContext(), SearchActivity.class);
            intent.putExtra(android.app.SearchManager.QUERY, searchQuery.getText().toString());
            getContext().startActivity(intent);
        }

        private void executeEndlessSpiceRequest() {
            long cacheExpiryDuration = useCache ? DurationInMillis.ONE_HOUR * 6 : DurationInMillis.ALWAYS_EXPIRED;
            spiceManager.execute(AptoideUtils.RepoUtils.buildSearchRequest(query, SearchRequest.SEARCH_LIMIT, SearchRequest.OTHER_REPOS_SEARCH_LIMIT, offset, storeName), MoreSearchActivity.class.getSimpleName() + query + offset, cacheExpiryDuration, new RequestListener<SearchResults>() {
                @Override
                public void onRequestFailure(SpiceException spiceException) {
                    if (mLoading && !displayableList.isEmpty()) {
                        displayableList.remove(displayableList.size() - 1);
                        getAdapter().notifyItemRemoved(displayableList.size());
                    }
                }

                @Override
                public void onRequestSuccess(SearchResults searchResults) {

                    if (mLoading && !displayableList.isEmpty()) {
                        displayableList.remove(displayableList.size() - 1);
                        getAdapter().notifyItemRemoved(displayableList.size());
                    }

                    List<SearchApk> apkList = searchResults.apkList;
                    if (!apkList.isEmpty()) {
                        displayableList.addAll(apkList);
                    }
                    offset += apkList.size();
                    getAdapter().notifyDataSetChanged();
                    swipeContainer.setEnabled(false);
                    mLoading = false;
                }
            });

        }
    }
}
