package com.aptoide.models.placeholders;

import com.aptoide.models.Displayable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by fabio on 20-10-2015.
 */
public class CommentPlaceHolderRow extends Displayable {

    public CommentPlaceHolderRow(@JsonProperty("BUCKETSIZE") int bucketSize) {
        super(bucketSize);
    }

    @Override
    public int getSpanSize() {
        return FULL_ROW;
    }

}