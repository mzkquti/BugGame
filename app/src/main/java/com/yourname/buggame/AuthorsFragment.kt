package com.yourname.buggame

import android.os.Bundle
import android.view.View
import android.widget.ListView
import androidx.fragment.app.Fragment

class AuthorsFragment : Fragment(R.layout.fragment_authors) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authors = listOf(
            Author("Ковалев Даниил Витальевич", R.drawable.photo_kovalev)
        )

        view.findViewById<ListView>(R.id.lvAuthors).adapter =
            AuthorsAdapter(requireContext(), authors)
    }
}