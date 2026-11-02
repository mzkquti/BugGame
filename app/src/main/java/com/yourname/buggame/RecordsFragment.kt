package com.yourname.buggame

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yourname.buggame.data.AppDatabase
import com.yourname.buggame.data.RecordRow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordsFragment : Fragment(R.layout.fragment_records) {

    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.lvRecords)
        tvEmpty = view.findViewById(R.id.tvEmpty)
    }

    // Вкладка стала активной — обновляем таблицу рекордов
    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = AppDatabase.get(requireContext()).resultDao().getTopRecords()
            listView.adapter = RecordsAdapter(requireContext(), rows)
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private class RecordsAdapter(
        private val context: Context,
        private val rows: List<RecordRow>
    ) : BaseAdapter() {

        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.item_record, parent, false)
            val row = rows[position]
            view.findViewById<TextView>(R.id.tvRecordTitle).text =
                "${position + 1}. ${row.fullName} — ${row.score}"
            view.findViewById<TextView>(R.id.tvRecordDetails).text =
                "Сложность: ${row.difficulty}, курс: ${row.course}\n" +
                    "Попадания: ${row.hits}, промахи: ${row.misses}\n" +
                    dateFormat.format(Date(row.playedAt))
            return view
        }
    }
}
