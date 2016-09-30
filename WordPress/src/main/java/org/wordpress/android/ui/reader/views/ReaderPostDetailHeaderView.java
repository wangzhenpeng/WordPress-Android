package org.wordpress.android.ui.reader.views;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.datasets.ReaderBlogTable;
import org.wordpress.android.models.ReaderBlog;
import org.wordpress.android.models.ReaderPost;
import org.wordpress.android.ui.reader.ReaderActivityLauncher;
import org.wordpress.android.ui.reader.actions.ReaderActions;
import org.wordpress.android.ui.reader.actions.ReaderBlogActions;
import org.wordpress.android.ui.reader.utils.ReaderUtils;
import org.wordpress.android.util.GravatarUtils;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.widgets.WPNetworkImageView;

/**
 * topmost view in post detail
 */
public class ReaderPostDetailHeaderView extends LinearLayout {

    private ReaderPost mPost;
    private ReaderFollowButton mFollowButton;

    public ReaderPostDetailHeaderView(Context context) {
        super(context);
        initView(context);
    }

    public ReaderPostDetailHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public ReaderPostDetailHeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        View view = inflate(context, R.layout.reader_post_detail_header_view, this);
        mFollowButton = (ReaderFollowButton) view.findViewById(R.id.header_follow_button);
    }

    public void setPost(@NonNull ReaderPost post) {
        mPost = post;

        TextView txtTitle = (TextView) findViewById(R.id.text_header_title);
        TextView txtAuthorName = (TextView) findViewById(R.id.text_header_author_name);
        WPNetworkImageView imgAvatar = (WPNetworkImageView) findViewById(R.id.image_header_avatar);

        boolean hasBlogName = mPost.hasBlogName();
        boolean hasAuthorName = mPost.hasAuthorName();

        if (hasBlogName && hasAuthorName) {
            txtTitle.setText(mPost.getBlogName());
            // don't show author name if it's the same as the blog name
            if (mPost.getAuthorName().equals(mPost.getBlogName())) {
                txtAuthorName.setVisibility(View.GONE);
            } else {
                txtAuthorName.setText(mPost.getAuthorName());
                txtAuthorName.setVisibility(View.VISIBLE);
            }
        } else if (hasBlogName) {
            txtTitle.setText(mPost.getBlogName());
            txtAuthorName.setVisibility(View.GONE);
        } else if (hasAuthorName) {
            txtTitle.setText(mPost.getAuthorName());
            txtAuthorName.setVisibility(View.GONE);
        } else {
            txtTitle.setText(R.string.untitled);
            txtAuthorName.setVisibility(View.GONE);
        }

        // show blog preview when these views are tapped
        txtTitle.setOnClickListener(mClickListener);
        txtAuthorName.setOnClickListener(mClickListener);
        imgAvatar.setOnClickListener(mClickListener);

        if (ReaderUtils.isLoggedOutReader()) {
            mFollowButton.setVisibility(View.GONE);
        } else {
            mFollowButton.setVisibility(View.VISIBLE);
            mFollowButton.setIsFollowed(mPost.isFollowedByCurrentUser);
            mFollowButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFollowStatus();
                }
            });
        }

        if (mPost.hasPostAvatar()) {
            int avatarSize = getContext().getResources().getDimensionPixelSize(R.dimen.avatar_sz_large);
            String avatarUrl = GravatarUtils.fixGravatarUrl(mPost.getPostAvatar(), avatarSize);
            imgAvatar.setImageUrl(avatarUrl, WPNetworkImageView.ImageType.AVATAR);
        } else {
            imgAvatar.showDefaultGravatarImage();
        }

        // TODO: show blavatar
        if (mPost.hasBlogUrl()) {
            int blavatarSz = getResources().getDimensionPixelSize(R.dimen.avatar_sz_medium);
            String blavatarUrl = GravatarUtils.blavatarFromUrl(mPost.getBlogUrl(), blavatarSz);
            //imgBlavatar.setImageUrl(imageUrl, WPNetworkImageView.ImageType.BLAVATAR);
        } else {
            //imgBlavatar.showDefaultBlavatarImage();
        }

        showFollowerCount();
    }

    private void showFollowerCount() {
        // show current follower count
        ReaderBlog blogInfo = mPost.feedId != 0 ? ReaderBlogTable.getFeedInfo(mPost.feedId) : ReaderBlogTable.getBlogInfo(mPost.blogId);
        if (blogInfo != null) {
            setFollowerCount(blogInfo.numSubscribers);
        }

        // update blog info if it's time or it doesn't exist
        if (blogInfo == null || ReaderBlogTable.isTimeToUpdateBlogInfo(blogInfo)) {
            ReaderActions.UpdateBlogInfoListener listener = new ReaderActions.UpdateBlogInfoListener() {
                @Override
                public void onResult(ReaderBlog serverBlogInfo) {
                    setFollowerCount(serverBlogInfo.numSubscribers);
                }
            };
            if (mPost.feedId != 0) {
                ReaderBlogActions.updateFeedInfo(mPost.feedId, null, listener);
            } else {
                ReaderBlogActions.updateBlogInfo(mPost.blogId, null, listener);
            }
        }
    }

    private void setFollowerCount(int count) {
        TextView txtFollowerCount = (TextView) findViewById(R.id.text_header_follow_count);
        txtFollowerCount.setText(String.format(getContext().getString(R.string.reader_label_follow_count), count));
    }

    /*
     * click listener which shows blog preview
     */
    private final OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mPost != null) {
                ReaderActivityLauncher.showReaderBlogPreview(v.getContext(), mPost);
            }
        }
    };

    private void toggleFollowStatus() {
        if (!NetworkUtils.checkConnection(getContext())) {
            return;
        }

        final boolean isAskingToFollow = !mPost.isFollowedByCurrentUser;

        ReaderActions.ActionListener listener = new ReaderActions.ActionListener() {
            @Override
            public void onActionResult(boolean succeeded) {
                if (getContext() == null) {
                    return;
                }
                mFollowButton.setEnabled(true);
                if (!succeeded) {
                    int errResId = isAskingToFollow ? R.string.reader_toast_err_follow_blog : R.string.reader_toast_err_unfollow_blog;
                    ToastUtils.showToast(getContext(), errResId);
                    mFollowButton.setIsFollowed(!isAskingToFollow);
                }
            }
        };

        // disable follow button until API call returns
        mFollowButton.setEnabled(false);

        boolean result;
        if (mPost.feedId != 0) {
            result = ReaderBlogActions.followFeedById(mPost.feedId, isAskingToFollow, listener);
        } else {
            result = ReaderBlogActions.followBlogById(mPost.blogId, isAskingToFollow, listener);
        }

        if (result) {
            mFollowButton.setIsFollowedAnimated(isAskingToFollow);
        }
    }
}