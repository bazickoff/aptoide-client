package cm.aptoide.pt.adapter.main;

import android.app.Activity;
import android.support.annotation.ColorInt;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aptoide.amethyst.models.EnumStoreTheme;
import com.aptoide.models.AppItem;
import com.aptoide.models.CommentItem;
import com.aptoide.models.Displayable;
import com.aptoide.models.HeaderRow;
import com.aptoide.models.ReviewRowItem;
import com.aptoide.models.placeholders.CommentPlaceHolderRow;
import com.aptoide.models.placeholders.ReviewPlaceHolderRow;

import java.util.List;

import cm.aptoide.pt.R;
import cm.aptoide.pt.adapter.BaseAdapter;
import cm.aptoide.pt.viewholders.BaseViewHolder;
import cm.aptoide.pt.viewholders.main.EmptyViewHolder;
import cm.aptoide.pt.viewholders.main.HeaderViewHolder;
import cm.aptoide.pt.viewholders.main.ReviewViewHolder;
import cm.aptoide.pt.viewholders.main.TopAppViewHolder;
import cm.aptoide.pt.viewholders.store.CommentViewHolder;

/**
 * Created by rmateus on 02/06/15.
 */
public class CommunityTabAdapter extends BaseAdapter {
    private Activity activity;
    @ColorInt private int colorResId;

    /**
     * Community's adapter
     *
     * @param displayableList Displayable list to show
     * @param activity
     * @param colorResId      if colorResId < 0, the default color will be used
     */
    public CommunityTabAdapter(List<Displayable> displayableList, Activity activity, int colorResId) {
        super(displayableList);
        this.activity = activity;
        if (colorResId < 0 && activity != null) {
            this.colorResId = activity.getResources().getColor(R.color.default_color);
        } else {
            this.colorResId = colorResId;
        }
    }

    @Override
    public BaseViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        BaseViewHolder holder;
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(viewType, viewGroup, false);

        switch (viewType) {
            case R.layout.layout_header:
                holder = new HeaderViewHolder(view, viewType, EnumStoreTheme.APTOIDE_STORE_THEME_DEFAULT);
                break;
            case R.layout.top_app_row:
                holder = new TopAppViewHolder(view, viewType);
                break;
            case R.layout.comment_row:
                holder = new CommentViewHolder(view, viewType, activity, colorResId);
                break;
            case R.layout.row_review:
                holder = new ReviewViewHolder(view, viewType, EnumStoreTheme.APTOIDE_STORE_THEME_DEFAULT);
                break;
            case R.layout.row_empty:
                holder = new EmptyViewHolder(view, viewType);
                break;
            default:
                throw new IllegalStateException(CommunityTabAdapter.class.getSimpleName() + " with unknown viewtype");
        }
        return holder;
    }

    @Override
    public int getItemViewType(int position) {

        Displayable displayable = displayableList.get(position);
        if (displayable instanceof HeaderRow) {
            return R.layout.layout_header;
        } else if (displayable instanceof AppItem) {
            return R.layout.top_app_row;
        } else if (displayable instanceof CommentItem) {
            return R.layout.comment_row;
        } else if (displayable instanceof ReviewRowItem) {
            return R.layout.row_review;
        } else if (displayableList.get(position) instanceof ReviewPlaceHolderRow) {
            return R.layout.row_empty;
        } else if (displayableList.get(position) instanceof CommentPlaceHolderRow) {
            return R.layout.row_empty;
        } else {
            throw new IllegalStateException("This adapter doesn't know how to show " + displayableList.get(position).getClass().getName());
        }
    }
}
