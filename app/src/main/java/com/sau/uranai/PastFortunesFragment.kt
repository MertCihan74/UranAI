package com.sau.uranai

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager


class PastFortunesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_pastfortunes, container, false)

        setupCardViewListeners(view)
        return view
    }
    private fun setupCardViewListeners(view: View) {

        view.findViewById<CardView>(R.id.PastFortunes_cardView).setOnClickListener {
            Toast.makeText(requireContext(), "Coming Soon...", Toast.LENGTH_SHORT).show()
        }

    }
}