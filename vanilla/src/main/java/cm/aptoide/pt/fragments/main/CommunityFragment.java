package cm.aptoide.pt.fragments.main;

import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.aptoide.amethyst.Aptoide;
import com.aptoide.amethyst.dialogs.AdultHiddenDialog;
import com.aptoide.amethyst.utils.AptoideUtils;
import com.aptoide.amethyst.utils.Logger;
import com.aptoide.dataprovider.webservices.AllCommentsRequest;
import com.aptoide.dataprovider.webservices.GetReviews;
import com.aptoide.dataprovider.webservices.json.review.Review;
import com.aptoide.dataprovider.webservices.json.review.ReviewListJson;
import com.aptoide.dataprovider.webservices.models.Constants;
import com.aptoide.dataprovider.webservices.models.Defaults;
import com.aptoide.dataprovider.webservices.models.StoreHomeTab;
import com.aptoide.dataprovider.webservices.models.v2.Comment;
import com.aptoide.dataprovider.webservices.models.v2.GetComments;
import com.aptoide.models.AdultItem;
import com.aptoide.models.CommentItem;
import com.aptoide.models.Displayable;
import com.aptoide.models.HeaderRow;
import com.aptoide.models.ReviewRowItem;
import com.aptoide.models.placeholders.CommentPlaceHolderRow;
import com.aptoide.models.placeholders.ReviewPlaceHolderRow;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;

import cm.aptoide.pt.R;
import cm.aptoide.pt.adapter.BaseAdapter;
import cm.aptoide.pt.adapter.main.CommunityTabAdapter;
import cm.aptoide.pt.fragments.store.BaseWebserviceFragment;

import static com.aptoide.dataprovider.webservices.models.v7.GetStoreWidgets.Datalist.WidgetList.COMMENTS_TYPE;
import static com.aptoide.dataprovider.webservices.models.v7.GetStoreWidgets.Datalist.WidgetList.REVIEWS_TYPE;

/**
 * Created by rmateus on 02/06/15.
 */
public class CommunityFragment extends BaseWebserviceFragment {

    protected RequestListener<StoreHomeTab> listener = new RequestListener<StoreHomeTab>() {
        @Override
        public void onRequestFailure(SpiceException spiceException) {
            handleErrorCondition(spiceException);
        }

        @Override
        public void onRequestSuccess(StoreHomeTab tab) {
            handleSuccessCondition();

            adapter = getAdapter();
            setRecyclerAdapter(adapter);

            displayableList.clear();
            if (isStorePage()) {
                displayableList.add(getStoreHeaderRow(tab));
            }

            displayableList.addAll(tab.list);

            if (isHomePage()) {
                displayableList.add(new AdultItem(BUCKET_SIZE));
            }
            for (Displayable row : tab.list) {

                if (row instanceof CommentPlaceHolderRow) {
                    executeCommentsRequest();
                } else if (row instanceof ReviewPlaceHolderRow) {
                    executeReviewsSpiceRequest();
                }
            }

            // check for hidden items
            if (tab.hidden > 0 && AptoideUtils.getSharedPreferences().getBoolean(Constants.SHOW_ADULT_HIDDEN, true) && getFragmentManager().findFragmentByTag(Constants.HIDDEN_ADULT_DIALOG) == null) {
                new AdultHiddenDialog().show(getFragmentManager(), Constants.HIDDEN_ADULT_DIALOG);
            }
        }
    };

    @Override
    protected BaseAdapter getAdapter() {
        if (adapter == null) {
            adapter = new CommunityTabAdapter(displayableList, getActivity(), -1);
        }
        return adapter;
    }

    @Override
    protected String getBaseContext() {
        return "community";
    }

    public static Fragment newInstance() {
        return new CommunityFragment();
    }

    @Override
    public void setLayoutManager(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
    }

    @Override
    protected void executeSpiceRequest(boolean useCache) {
        this.useCache = useCache;
        long cacheExpiryDuration = useCache ? DurationInMillis.ONE_HOUR * 6 : DurationInMillis.ALWAYS_EXPIRED;

        // in order to present the right info on screen after a screen rotation, always pass the bucketsize as cachekey
        spiceManager.execute(
                //AptoideUtils.RepoUtils.buildStoreRequest(getStoreId(), getFakeString(), getFakeString(), getBaseContext()),
                AptoideUtils.RepoUtils.buildStoreRequest(getStoreId(), getBaseContext()),
                getBaseContext() + "-" + getStoreId() + "-" + BUCKET_SIZE + "-" + AptoideUtils.getSharedPreferences().getBoolean(Constants.MATURE_CHECK_BOX, false),
                cacheExpiryDuration,
                listener);
    }

    private void executeReviewsSpiceRequest() {

        GetReviews.GetReviewList reviewRequest = new GetReviews.GetReviewList();

        reviewRequest.setOrderBy("id");
        reviewRequest.homePage = isHomePage();
        reviewRequest.limit = 7;

        spiceManager.execute(reviewRequest, "review-community-store-", useCache ? DurationInMillis.ONE_HOUR : DurationInMillis.ALWAYS_EXPIRED, new RequestListener<ReviewListJson>() {
            @Override
            public void onRequestFailure(SpiceException spiceException) {
                handleErrorCondition(spiceException);
            }

            @Override
            public void onRequestSuccess(ReviewListJson reviewListJson) {
                if ("OK".equals(reviewListJson.status) && reviewListJson.reviews != null && reviewListJson.reviews.size() > 0) {

                    // range is the size of the list. Because the HeaderRow replaces the placeholder, it's not considered an insertion
                    // why is this important? because of notifyItemRangeInserted
                    int range = reviewListJson.reviews.size();
                    int index = 0, originalIndex = 0;

                    boolean reviewPlaceHolderFound = false;
                    for (Displayable display : displayableList) {
                        if (display instanceof ReviewPlaceHolderRow) {
                            reviewPlaceHolderFound = true;
                            originalIndex = index = displayableList.indexOf(display);
                            break;
                        }
                    }

                    // prevent multiple requests adding to the beginning of the list
                    if (!reviewPlaceHolderFound)
                        return;

                    HeaderRow header = new HeaderRow(getString(R.string.reviews), true, REVIEWS_TYPE, BUCKET_SIZE, isHomePage(), Constants.GLOBAL_STORE);
                    displayableList.set(index++, header);

                    int i = 0;
                    for (Review review : reviewListJson.reviews) {

                        ReviewRowItem reviewRowItem = getReviewRow(review);
                        displayableList.add(index++, reviewRowItem);
                    }

                    getAdapter().notifyItemRangeInserted(originalIndex + 1, range);
                }
            }
        });
    }

    private void executeCommentsRequest() {
        long cacheExpiryDuration = useCache ? DurationInMillis.ONE_HOUR * 6 : DurationInMillis.ALWAYS_EXPIRED;

        AllCommentsRequest request = new AllCommentsRequest();
        request.storeName = getStoreName();
        request.filters = Aptoide.filters;
        request.lang = AptoideUtils.StringUtils.getMyCountryCode(getContext());
        request.limit = 7;

        spiceManager.execute(request, getBaseContext() + getStoreId(), cacheExpiryDuration, new RequestListener<GetComments>() {
                    @Override
                    public void onRequestFailure(SpiceException spiceException) {
                        Logger.printException(spiceException);
                    }

                    @Override
                    public void onRequestSuccess(GetComments get) {
                        if ("OK".equals(get.status) && get.list != null && get.list.size() > 0) {


                            // range is the size of the list. Because the HeaderRow replaces the placeholder, it's not considered an insertion
                            // why is this important? because of notifyItemRangeInserted
                            int range = get.list.size();
                            int index = 0, originalIndex = 0;

                            boolean placeHolderFound = false;
                            for (Displayable display : displayableList) {
                                if (display instanceof CommentPlaceHolderRow) {
                                    placeHolderFound = true;
                                    originalIndex = index = displayableList.indexOf(display);
                                    break;
                                }
                            }

                            // prevent multiple requests adding to beginning of the list
                            if (!placeHolderFound) {
                                return;
                            }

                            HeaderRow header = new HeaderRow(getString(R.string.comments), true, COMMENTS_TYPE, BUCKET_SIZE, isHomePage(), getStoreId());
                            displayableList.set(index++, header);

                            for (int i = 0; i < get.list.size(); i++) {
//                            for (int i = 0; i < 7; i++) {
                                Comment comment = get.list.get(i);

                                CommentItem commentItem = getCommentRow(comment);
                                displayableList.add(index++, commentItem);
                            }

                            getAdapter().notifyItemRangeInserted(originalIndex + 1, range);
                        }
                    }
                }
        );
    }

    private CommentItem getCommentRow(Comment comment) {
        CommentItem item = new CommentItem(BUCKET_SIZE);
        item.appname = comment.getAppname();
        item.id = comment.getId();
        item.lang = comment.getLang();
        item.text = comment.getText();
        item.timestamp = comment.getTimestamp();
        item.useravatar = comment.getUseravatar();
        item.appid = comment.getAppid();
        item.username = comment.getUsername();
        return item;
    }

    @Override
    protected long getStoreId() {
        return Defaults.DEFAULT_STORE_ID;
    }

    @Override
    public String getStoreName() {
        return Defaults.DEFAULT_STORE_NAME;
    }

}
