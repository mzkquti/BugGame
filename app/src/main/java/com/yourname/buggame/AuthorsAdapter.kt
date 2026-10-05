package com.yourname.buggame

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AuthorsAdapter(context: Context, private val authors: List<Author>) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount() = authors.size
    override fun getItem(position: Int) = authors[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_author, parent, false)
        val author = authors[position]
        view.findViewById<ImageView>(R.id.ivPhoto).setImageResource(author.photoRes)
        view.findViewById<TextView>(R.id.tvName).text = author.name
        return view
    }
}