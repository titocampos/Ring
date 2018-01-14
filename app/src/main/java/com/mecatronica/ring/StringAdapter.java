package com.mecatronica.ring;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class StringAdapter extends ArrayAdapter<StringItem> {

    public StringAdapter(Context context, ArrayList<StringItem> words) {
        super(context, 0, words);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View listItemView = convertView;
        if (listItemView == null) {
            listItemView = LayoutInflater.from(getContext()).inflate(
                    R.layout.list_item, parent, false);
        }

        StringItem item = getItem(position);

        TextView acao_text_view = (TextView) listItemView.findViewById(R.id.acao_text_view);

        acao_text_view.setText(item.getAction());

        TextView dscricao_text_view = (TextView) listItemView.findViewById(R.id.dscricao_text_view);

        dscricao_text_view.setText(item.getDescripcion());

        ImageView imageView = (ImageView) listItemView.findViewById(R.id.image);
        if (item.hasImage()) {
            imageView.setImageResource(item.getImageResourceId());
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.GONE);
        }
        return listItemView;
    }
}