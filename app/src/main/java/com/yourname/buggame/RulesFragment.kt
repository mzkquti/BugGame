package com.yourname.buggame

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment

class RulesFragment : Fragment(R.layout.fragment_rules) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvRules).text =
            HtmlCompat.fromHtml(getString(R.string.rules_html), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}