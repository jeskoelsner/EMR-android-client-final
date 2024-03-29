package org.zlwima.emurgency.mqtt.android.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.androidquery.AQuery;
import java.util.ArrayList;
import org.zlwima.emurgency.mqtt.R;
import org.zlwima.emurgency.mqtt.android.adapter.objects.ParcelableNewsObject;

public class NewsAdapter extends ArrayAdapter<ParcelableNewsObject> {
		private final static int RESSOURCE = R.layout.part_newslist_element;
		private final LayoutInflater layoutInflater;

		static class ViewHolder {
				public TextView newsTitle;
				public TextView newsDescription;
				public ImageView newsThumb;
				public ProgressBar newsAvatarProgress;
		}

		public NewsAdapter(Context context) {
				super(context, RESSOURCE, new ArrayList<ParcelableNewsObject>());
				this.setNotifyOnChange(true); //autonotify if list changes

				//prepare inflater and receiver
				layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public ParcelableNewsObject getItem(int position) {
				return super.getItem(position); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
				ViewHolder viewHolder;
				if (convertView == null) {
						convertView = layoutInflater.inflate(RESSOURCE, null);

						viewHolder = new ViewHolder();
						viewHolder.newsTitle = (TextView) convertView.findViewById(R.id.newsTitle);
						viewHolder.newsDescription = (TextView) convertView.findViewById(R.id.newsAutor);
						viewHolder.newsThumb = (ImageView) convertView.findViewById(R.id.newsAvatar);
						viewHolder.newsAvatarProgress = (ProgressBar) convertView.findViewById(R.id.newsAvatarProgress);
						convertView.setTag(viewHolder);
				} else {
						viewHolder = (ViewHolder) convertView.getTag();
				}

				AQuery aq = new AQuery(convertView);
				aq.id(viewHolder.newsThumb).progress(viewHolder.newsAvatarProgress).image(
						getItem(position).getThumbnail(), true, true, 0, R.drawable.logo_small, null, 0, 1.0f);

				viewHolder.newsTitle.setText(getItem(position).getTitle());
				viewHolder.newsDescription.setText(getItem(position).getDescription());
				//holder.newsAvatar.setImageURI(Uri.parse(getItem(position).getActor().getImage()));

				return convertView;
		}

}
