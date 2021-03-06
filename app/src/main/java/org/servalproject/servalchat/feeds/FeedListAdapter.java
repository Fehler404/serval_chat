package org.servalproject.servalchat.feeds;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.servalproject.mid.FeedList;
import org.servalproject.servalchat.R;
import org.servalproject.servalchat.views.BasicViewHolder;
import org.servalproject.servalchat.views.ScrollingAdapter;
import org.servalproject.servaldna.BundleId;
import org.servalproject.servaldna.Subscriber;
import org.servalproject.servaldna.rhizome.RhizomeListBundle;

import java.util.HashSet;

/**
 * Created by jeremy on 11/10/16.
 */
public class FeedListAdapter extends ScrollingAdapter<RhizomeListBundle, FeedListAdapter.FeedHolder> {

	private final PublicFeedsPresenter presenter;
	private HashSet<BundleId> bundles = new HashSet<>();

	public FeedListAdapter(FeedList list, PublicFeedsPresenter presenter) {
		super(list);
		this.presenter = presenter;
	}

	private void removeItem(BundleId id) {
		// find the old item and remove it.
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).manifest.id.equals(id)) {
				items.remove(i);
				return;
			}
		}
	}

	@Override
	protected void addItem(int index, RhizomeListBundle item) {
		if (bundles.contains(item.manifest.id)) {
			if (index == items.size())
				return;
			removeItem(item.manifest.id);
		}else{
			bundles.add(item.manifest.id);
		}
		super.addItem(index, item);
	}

	@Override
	protected void bind(FeedHolder holder, RhizomeListBundle item) {
		holder.bind(item);
	}

	@Override
	public FeedHolder create(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		return new FeedHolder(inflater.inflate(R.layout.feed, parent, false));
	}

	public class FeedHolder extends BasicViewHolder implements View.OnClickListener {
		private TextView name;
		private Subscriber subscriber;

		public FeedHolder(View itemView) {
			super(itemView);
			this.name = (TextView) this.itemView.findViewById(R.id.name);
			this.itemView.setOnClickListener(this);
		}

		public void bind(RhizomeListBundle item) {
			subscriber = new Subscriber(
					item.author != null ? item.author : item.manifest.sender,
					item.manifest.id, true);
			if (item.manifest.name == null || "".equals(item.manifest.name))
				name.setText(subscriber.sid.abbreviation());
			else
				name.setText(item.manifest.name);
		}

		@Override
		public void onClick(View v) {
			presenter.openFeed(subscriber);
		}
	}
}
